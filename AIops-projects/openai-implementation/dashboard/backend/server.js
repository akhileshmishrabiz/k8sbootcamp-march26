const express = require('express');
const cors = require('cors');
const path = require('path');
const { Pool } = require('pg');
const axios = require('axios');

const app = express();
const PORT = process.env.PORT || 3001;

// OpenAI Configuration
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const OPENAI_API_URL = 'https://api.openai.com/v1/chat/completions';

if (!OPENAI_API_KEY) {
  console.warn('WARNING: OPENAI_API_KEY not set. Chat functionality will not work.');
}

// Database configuration
const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'failuredb',
  user: process.env.DB_USER || 'postgres',
  password: process.env.DB_PASSWORD || 'postgres',
  port: 5432,
});

// Java app URL
const JAVA_APP_URL = process.env.JAVA_APP_URL || 'http://localhost:8080';

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Health endpoint
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date() });
});

// Get current application status
app.get('/api/status', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM app_status WHERE id = 1'
    );

    if (result.rows.length === 0) {
      return res.json({
        current_status: 'unknown',
        last_check_time: null,
        failure_count: 0
      });
    }

    res.json(result.rows[0]);
  } catch (error) {
    console.error('Error fetching status:', error);
    res.status(500).json({ error: 'Failed to fetch status' });
  }
});

// Get health check history
app.get('/api/health-checks', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 20;
    const result = await pool.query(
      'SELECT * FROM health_checks ORDER BY timestamp DESC LIMIT $1',
      [limit]
    );

    res.json(result.rows);
  } catch (error) {
    console.error('Error fetching health checks:', error);
    res.status(500).json({ error: 'Failed to fetch health checks' });
  }
});

// Get latest health check
app.get('/api/health-checks/latest', async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM health_checks ORDER BY timestamp DESC LIMIT 1'
    );

    if (result.rows.length === 0) {
      return res.json(null);
    }

    res.json(result.rows[0]);
  } catch (error) {
    console.error('Error fetching latest health check:', error);
    res.status(500).json({ error: 'Failed to fetch latest health check' });
  }
});

// Trigger failure on Java app
app.post('/api/trigger-failure/:type', async (req, res) => {
  const { type } = req.params;

  try {
    const response = await axios.post(
      `${JAVA_APP_URL}/api/failure/trigger/${type}`
    );

    res.json(response.data);
  } catch (error) {
    console.error('Error triggering failure:', error.message);
    res.status(500).json({
      error: 'Failed to trigger failure',
      details: error.message
    });
  }
});

// Clear failure on Java app
app.post('/api/clear-failure/:type', async (req, res) => {
  const { type } = req.params;

  try {
    const response = await axios.post(
      `${JAVA_APP_URL}/api/failure/clear/${type}`
    );

    res.json(response.data);
  } catch (error) {
    console.error('Error clearing failure:', error.message);
    res.status(500).json({
      error: 'Failed to clear failure',
      details: error.message
    });
  }
});

// Clear all failures
app.post('/api/clear-all-failures', async (req, res) => {
  try {
    const response = await axios.post(
      `${JAVA_APP_URL}/api/failure/clear-all`
    );

    res.json(response.data);
  } catch (error) {
    console.error('Error clearing all failures:', error.message);
    res.status(500).json({
      error: 'Failed to clear all failures',
      details: error.message
    });
  }
});

// Get failure status from Java app
app.get('/api/failure-status', async (req, res) => {
  try {
    const response = await axios.get(
      `${JAVA_APP_URL}/api/failure/status`
    );

    res.json(response.data);
  } catch (error) {
    console.error('Error fetching failure status:', error.message);
    res.status(500).json({
      error: 'Failed to fetch failure status',
      details: error.message
    });
  }
});

// Get application logs from database
app.get('/api/logs', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 50;
    const result = await pool.query(
      'SELECT * FROM health_checks WHERE status = $1 ORDER BY timestamp DESC LIMIT $2',
      ['unhealthy', limit]
    );

    // Combine error logs
    const logs = result.rows.map(row => ({
      timestamp: row.timestamp,
      logs: row.raw_logs || row.error_summary || 'No logs available'
    }));

    res.json(logs);
  } catch (error) {
    console.error('Error fetching logs:', error);
    res.status(500).json({ error: 'Failed to fetch logs' });
  }
});

// Chat with OpenAI - Analyze logs and provide recommendations
app.post('/api/chat', async (req, res) => {
  const { message } = req.body;

  if (!message) {
    return res.status(400).json({ error: 'Message is required' });
  }

  if (!OPENAI_API_KEY) {
    return res.status(500).json({
      error: 'OpenAI API key not configured',
      details: 'Please set OPENAI_API_KEY environment variable'
    });
  }

  try {
    // Get recent error logs from database
    const logsResult = await pool.query(
      'SELECT * FROM health_checks WHERE status = $1 ORDER BY timestamp DESC LIMIT 5',
      ['unhealthy']
    );

    let context = '';
    if (logsResult.rows.length > 0) {
      context = 'Recent application error logs:\n\n';
      logsResult.rows.forEach((row, index) => {
        context += `Error ${index + 1} (${row.timestamp}):\n`;
        context += `${row.raw_logs || row.error_summary}\n\n`;
      });
    }

    // Build system message with context
    const systemMessage = `You are an expert DevOps engineer and application troubleshooting specialist.
You analyze application logs, identify root causes of failures, and provide actionable solutions.

The application is a Spring Boot Java microservices application running on Kubernetes that simulates realistic production failures.

Available failure types:
1. HTTP 409 Conflict - Duplicate resource creation
2. HTTP 404 Not Found - Entity lookup failure
3. BusinessException - Task object not found
4. JWT Expired - Okta authentication failure
5. Invalid UUID - Type mismatch from legacy systems
6. DB Constraint Violation - Database unique constraint violations
7. Malformed JSON Request - JSON parsing failures
8. Transaction Failure - JPA validation errors
9. Downstream Timeout - Service integration timeouts
10. Optimistic Lock - Concurrent update conflicts

When analyzing logs:
- Identify the exact failure type and root cause
- Explain what the error means in simple terms
- Provide step-by-step fix instructions
- Suggest prevention strategies
- Be concise but thorough

${context ? 'Current application context:\n' + context : ''}`;

    const messages = [
      { role: 'system', content: systemMessage },
      { role: 'user', content: message }
    ];

    console.log('Calling OpenAI API...');

    const response = await axios.post(
      OPENAI_API_URL,
      {
        model: 'gpt-4o-mini', // Fast and cost-effective
        messages: messages,
        temperature: 0.7,
        max_tokens: 1000
      },
      {
        headers: {
          'Authorization': `Bearer ${OPENAI_API_KEY}`,
          'Content-Type': 'application/json'
        },
        timeout: 30000
      }
    );

    const aiResponse = response.data.choices[0].message.content;

    res.json({
      response: aiResponse,
      model: 'gpt-4o-mini',
      context_used: logsResult.rows.length > 0
    });

  } catch (error) {
    console.error('Error calling OpenAI:', error.message);
    if (error.response) {
      console.error('OpenAI API Error:', error.response.data);
    }
    res.status(500).json({
      error: 'Failed to get AI response',
      details: error.message
    });
  }
});

// Analyze specific logs with OpenAI
app.post('/api/analyze-logs', async (req, res) => {
  const { logs } = req.body;

  if (!logs) {
    return res.status(400).json({ error: 'Logs are required' });
  }

  if (!OPENAI_API_KEY) {
    return res.status(500).json({
      error: 'OpenAI API key not configured',
      details: 'Please set OPENAI_API_KEY environment variable'
    });
  }

  try {
    const prompt = `Analyze these application logs and provide:
1. Root cause identification
2. Failure type (which of the 10 failure modes)
3. Immediate fix steps
4. Prevention recommendations

Logs:
${logs}`;

    const response = await axios.post(
      OPENAI_API_URL,
      {
        model: 'gpt-4o-mini',
        messages: [
          {
            role: 'system',
            content: 'You are an expert at analyzing Spring Boot application logs. Provide clear, actionable diagnostics.'
          },
          { role: 'user', content: prompt }
        ],
        temperature: 0.5,
        max_tokens: 1500
      },
      {
        headers: {
          'Authorization': `Bearer ${OPENAI_API_KEY}`,
          'Content-Type': 'application/json'
        },
        timeout: 30000
      }
    );

    const analysis = response.data.choices[0].message.content;

    res.json({
      analysis: analysis,
      model: 'gpt-4o-mini'
    });

  } catch (error) {
    console.error('Error analyzing logs:', error.message);
    res.status(500).json({
      error: 'Failed to analyze logs',
      details: error.message
    });
  }
});

// Serve React app for all other routes
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Start server
app.listen(PORT, () => {
  console.log(`Dashboard backend running on port ${PORT}`);
  console.log(`OpenAI integration: ${OPENAI_API_KEY ? 'ENABLED' : 'DISABLED (set OPENAI_API_KEY)'}`);
});
