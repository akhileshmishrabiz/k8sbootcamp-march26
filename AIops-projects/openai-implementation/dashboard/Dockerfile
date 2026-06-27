FROM node:18-alpine

WORKDIR /app

# Copy backend package files
COPY backend/package.json ./package.json
COPY backend/server.js ./server.js

# Install dependencies
RUN npm install --production

# Copy frontend build
COPY frontend/build ./public

# Expose port
EXPOSE 3001

# Start server
CMD ["node", "server.js"]
