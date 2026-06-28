import React, { useState, useEffect } from 'react';
import axios from 'axios';
import Chat from './Chat';
import Documents from './Documents';
import './App.css';

const API_URL = process.env.REACT_APP_API_URL || '';

function App() {
  const [currentView, setCurrentView] = useState('overview');
  const [status, setStatus] = useState(null);
  const [latestCheck, setLatestCheck] = useState(null);
  const [healthHistory, setHealthHistory] = useState([]);
  const [failureStatus, setFailureStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [darkMode, setDarkMode] = useState(false);

  const failureTypes = [
    { id: 'conflict_409', label: 'HTTP 409 Conflict — Duplicate User', icon: '🔄', category: 'HTTP Errors' },
    { id: 'not_found_404', label: 'HTTP 404 Not Found — Entity Missing', icon: '🔍', category: 'HTTP Errors' },
    { id: 'jwt_expired', label: 'JWT Expired — Okta Auth Failure', icon: '🔐', category: 'Auth Failures' },
    { id: 'business_exception', label: 'BusinessException — Task Object Not Found', icon: '💼', category: 'Data Errors' },
    { id: 'invalid_uuid', label: 'Invalid UUID — Type Mismatch', icon: '🔢', category: 'Data Errors' },
    { id: 'db_constraint_violation', label: 'DB Constraint Violation', icon: '🗄️', category: 'Data Errors' },
    { id: 'malformed_request', label: 'Malformed JSON Request', icon: '📝', category: 'Data Errors' },
    { id: 'transaction_failure', label: 'Transaction Commit Failure', icon: '💳', category: 'Data Errors' },
    { id: 'downstream_timeout', label: 'Downstream Service Timeout', icon: '⏱️', category: 'Integration Errors' },
    { id: 'optimistic_lock', label: 'Optimistic Lock — Concurrent Update', icon: '🔒', category: 'Integration Errors' }
  ];

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 10000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    document.body.className = darkMode ? 'dark-mode' : '';
  }, [darkMode]);

  const fetchData = async () => {
    try {
      const [statusRes, latestRes, historyRes, failureRes] = await Promise.all([
        axios.get(`${API_URL}/api/status`),
        axios.get(`${API_URL}/api/health-checks/latest`),
        axios.get(`${API_URL}/api/health-checks?limit=10`),
        axios.get(`${API_URL}/api/failure-status`)
      ]);

      setStatus(statusRes.data);
      setLatestCheck(latestRes.data);
      setHealthHistory(historyRes.data);
      setFailureStatus(failureRes.data);
    } catch (error) {
      console.error('Error fetching data:', error);
    }
  };

  const triggerFailure = async (type) => {
    setLoading(true);
    try {
      await axios.post(`${API_URL}/api/trigger-failure/${type}`);
      fetchData();
    } catch (error) {
      console.error(`Failed to trigger ${type}:`, error);
    }
    setLoading(false);
  };

  const clearFailure = async (type) => {
    setLoading(true);
    try {
      await axios.post(`${API_URL}/api/clear-failure/${type}`);
      fetchData();
    } catch (error) {
      console.error(`Failed to clear ${type}:`, error);
    }
    setLoading(false);
  };

  const clearAllFailures = async () => {
    setLoading(true);
    try {
      await axios.post(`${API_URL}/api/clear-all-failures`);
      fetchData();
    } catch (error) {
      console.error('Failed to clear all failures:', error);
    }
    setLoading(false);
  };

  const formatTimestamp = (timestamp) => {
    if (!timestamp) return 'N/A';
    return new Date(timestamp).toLocaleString();
  };

  const getActiveFailures = () => {
    if (!failureStatus?.failures) return [];
    return Object.entries(failureStatus.failures)
      .filter(([, active]) => active)
      .map(([type]) => type);
  };

  const activeFailures = getActiveFailures();
  const healthyCount = healthHistory.filter(h => h.status === 'healthy').length;
  const unhealthyCount = healthHistory.filter(h => h.status === 'unhealthy').length;

  return (
    <div className="App">
      {/* Sidebar */}
      <aside className={`sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}>
        <div className="sidebar-header">
          <div className="logo">
            <span className="logo-icon">🔍</span>
            {!sidebarCollapsed && <span className="logo-text">FailureWatch</span>}
          </div>
          <button className="collapse-btn" onClick={() => setSidebarCollapsed(!sidebarCollapsed)}>
            {sidebarCollapsed ? '→' : '←'}
          </button>
        </div>

        <nav className="sidebar-nav">
          <button
            className={`nav-item ${currentView === 'overview' ? 'active' : ''}`}
            onClick={() => setCurrentView('overview')}
          >
            <span className="nav-icon">📊</span>
            {!sidebarCollapsed && <span className="nav-text">Overview</span>}
          </button>
          <button
            className={`nav-item ${currentView === 'failures' ? 'active' : ''}`}
            onClick={() => setCurrentView('failures')}
          >
            <span className="nav-icon">⚙️</span>
            {!sidebarCollapsed && <span className="nav-text">Failure Controls</span>}
          </button>
          <button
            className={`nav-item ${currentView === 'history' ? 'active' : ''}`}
            onClick={() => setCurrentView('history')}
          >
            <span className="nav-icon">📜</span>
            {!sidebarCollapsed && <span className="nav-text">History</span>}
          </button>
          <button
            className={`nav-item ${currentView === 'chat' ? 'active' : ''}`}
            onClick={() => setCurrentView('chat')}
          >
            <span className="nav-icon">💬</span>
            {!sidebarCollapsed && <span className="nav-text">AI Assistant</span>}
          </button>
          <button
            className={`nav-item ${currentView === 'documents' ? 'active' : ''}`}
            onClick={() => setCurrentView('documents')}
          >
            <span className="nav-icon">📚</span>
            {!sidebarCollapsed && <span className="nav-text">Knowledge Base</span>}
          </button>
        </nav>

        <div className="sidebar-footer">
          <button className="theme-toggle" onClick={() => setDarkMode(!darkMode)}>
            <span className="nav-icon">{darkMode ? '☀️' : '🌙'}</span>
            {!sidebarCollapsed && <span className="nav-text">{darkMode ? 'Light' : 'Dark'} Mode</span>}
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="main-content">
        <header className="top-bar">
          <h1 className="page-title">
            {currentView === 'overview' && '📊 System Overview'}
            {currentView === 'failures' && '⚙️ Failure Controls'}
            {currentView === 'history' && '📜 Health Check History'}
            {currentView === 'chat' && '💬 AI Assistant'}
            {currentView === 'documents' && '📚 Knowledge Base'}
          </h1>
          <div className="top-bar-actions">
            <div className="status-indicator">
              <span className={`pulse ${status?.current_status || 'unknown'}`}></span>
              <span className="status-text">{status?.current_status || 'Loading...'}</span>
            </div>
          </div>
        </header>

        <div className="content-wrapper">
          {/* Overview View */}
          {currentView === 'overview' && (
            <>
              {/* Metrics Cards */}
              <div className="metrics-grid">
                <div className="metric-card">
                  <div className="metric-icon status-icon">
                    {status?.current_status === 'healthy' ? '✅' : '❌'}
                  </div>
                  <div className="metric-content">
                    <div className="metric-label">System Status</div>
                    <div className={`metric-value status-${status?.current_status}`}>
                      {status?.current_status?.toUpperCase() || 'UNKNOWN'}
                    </div>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon">🔴</div>
                  <div className="metric-content">
                    <div className="metric-label">Active Failures</div>
                    <div className="metric-value">{activeFailures.length}</div>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon">📊</div>
                  <div className="metric-content">
                    <div className="metric-label">Total Failures</div>
                    <div className="metric-value">{status?.failure_count || 0}</div>
                  </div>
                </div>

                <div className="metric-card">
                  <div className="metric-icon">⏰</div>
                  <div className="metric-content">
                    <div className="metric-label">Last Check</div>
                    <div className="metric-value-small">{formatTimestamp(status?.last_check_time)}</div>
                  </div>
                </div>
              </div>

              {/* Active Failures Alert */}
              {latestCheck && latestCheck.status === 'unhealthy' && (
                <div className="alert-card error">
                  <div className="alert-header">
                    <span className="alert-icon">🚨</span>
                    <h3>Active Error Detected</h3>
                  </div>
                  <div className="alert-body">
                    <pre className="error-summary">{latestCheck.error_summary}</pre>
                    <div className="alert-meta">
                      <span>🕐 {formatTimestamp(latestCheck.timestamp)}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Health Statistics */}
              <div className="stats-grid">
                <div className="stats-card">
                  <h3>Health Check Statistics</h3>
                  <div className="stats-content">
                    <div className="stat-item">
                      <span className="stat-label">✅ Healthy</span>
                      <span className="stat-value healthy">{healthyCount}</span>
                      <div className="stat-bar">
                        <div
                          className="stat-bar-fill healthy"
                          style={{width: `${(healthyCount / healthHistory.length) * 100}%`}}
                        ></div>
                      </div>
                    </div>
                    <div className="stat-item">
                      <span className="stat-label">❌ Unhealthy</span>
                      <span className="stat-value unhealthy">{unhealthyCount}</span>
                      <div className="stat-bar">
                        <div
                          className="stat-bar-fill unhealthy"
                          style={{width: `${(unhealthyCount / healthHistory.length) * 100}%`}}
                        ></div>
                      </div>
                    </div>
                  </div>
                </div>

                {activeFailures.length > 0 && (
                  <div className="stats-card active-failures">
                    <h3>Active Failures</h3>
                    <div className="active-failures-list">
                      {activeFailures.map(type => {
                        const failure = failureTypes.find(f => f.id === type);
                        return (
                          <div key={type} className="active-failure-item">
                            <span>{failure?.icon || '⚠️'}</span>
                            <span>{failure?.label || type}</span>
                            <button
                              className="mini-btn clear"
                              onClick={() => clearFailure(type)}
                            >
                              Clear
                            </button>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Recent History */}
              <div className="card">
                <h3>Recent Health Checks</h3>
                <div className="timeline">
                  {healthHistory.slice(0, 5).map((check, index) => (
                    <div key={index} className={`timeline-item ${check.status}`}>
                      <div className="timeline-marker"></div>
                      <div className="timeline-content">
                        <div className="timeline-header">
                          <span className={`timeline-badge ${check.status}`}>
                            {check.status === 'healthy' ? '✅' : '❌'} {check.status.toUpperCase()}
                          </span>
                          <span className="timeline-time">{formatTimestamp(check.timestamp)}</span>
                        </div>
                        <div className="timeline-body">{check.error_summary}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* Failures View */}
          {currentView === 'failures' && (
            <div className="card">
              <div className="card-header">
                <h3>Failure Simulation Controls</h3>
                <button className="btn btn-danger" onClick={clearAllFailures} disabled={loading}>
                  🔄 Clear All Failures
                </button>
              </div>

              {/* Group by category */}
              {['HTTP Errors', 'Auth Failures', 'Data Errors', 'Integration Errors'].map(category => {
                const categoryFailures = failureTypes.filter(f => f.category === category);
                if (categoryFailures.length === 0) return null;

                return (
                  <div key={category} className="failure-category">
                    <h4 className="category-title">{category}</h4>
                    <div className="failure-grid">
                      {categoryFailures.map(failure => (
                        <div key={failure.id} className={`failure-card ${failureStatus?.failures?.[failure.id] ? 'active' : ''}`}>
                          <div className="failure-card-header">
                            <span className="failure-icon">{failure.icon}</span>
                            <span className="failure-name">{failure.label}</span>
                            {failureStatus?.failures?.[failure.id] && (
                              <span className="active-badge">ACTIVE</span>
                            )}
                          </div>
                          <div className="failure-card-actions">
                            <button
                              onClick={() => triggerFailure(failure.id)}
                              disabled={loading || failureStatus?.failures?.[failure.id]}
                              className="btn btn-sm btn-trigger"
                            >
                              Trigger
                            </button>
                            <button
                              onClick={() => clearFailure(failure.id)}
                              disabled={loading || !failureStatus?.failures?.[failure.id]}
                              className="btn btn-sm btn-success"
                            >
                              Clear
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* History View */}
          {currentView === 'history' && (
            <div className="card">
              <h3>Complete Health Check History</h3>
              <div className="table-wrapper">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Timestamp</th>
                      <th>Summary</th>
                    </tr>
                  </thead>
                  <tbody>
                    {healthHistory.map((check, index) => (
                      <tr key={index} className={`row-${check.status}`}>
                        <td>
                          <span className={`table-badge ${check.status}`}>
                            {check.status === 'healthy' ? '✅' : '❌'} {check.status.toUpperCase()}
                          </span>
                        </td>
                        <td className="timestamp-cell">{formatTimestamp(check.timestamp)}</td>
                        <td className="summary-cell">{check.error_summary}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Chat View */}
          {currentView === 'chat' && <Chat />}

          {/* Documents View */}
          {currentView === 'documents' && <Documents />}
        </div>
      </main>
    </div>
  );
}

export default App;
