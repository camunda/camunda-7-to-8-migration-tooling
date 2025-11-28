#!/usr/bin/env node

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import livereload from 'livereload';
import path from 'path';
import { fileURLToPath } from 'url';

// Get __dirname equivalent in ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Watch the target directory where files are copied
const watchPath = path.resolve(__dirname, '../target/classes/plugin-webapp/migrator-plugin/app');

console.log('🔄 LiveReload server starting...');
console.log(`📁 Watching: ${watchPath}`);
console.log('🌐 LiveReload script should be automatically injected in development mode');

// Create LiveReload server with more verbose options
const server = livereload.createServer({
  port: 35729,
  exts: ['js', 'css', 'html'],
  debug: true,
  delay: 100
});

// Add event listeners for debugging
server.server.once('listening', () => {
  console.log('✅ LiveReload server is listening on port 35729');
});

server.server.on('connection', (socket) => {
  console.log('✅ Browser connected to LiveReload server');

  socket.on('close', () => {
    console.log('🔌 Browser disconnected from LiveReload server');
  });
});

// Watch the directory and manually trigger reload on changes
server.watch(watchPath);

// Override the refresh method to add logging
const originalRefresh = server.refresh;
server.refresh = function(filepath) {
  console.log(`🔄 File changed, triggering reload: ${filepath || 'unknown file'}`);
  console.log('📡 Sending reload signal to browser...');
  return originalRefresh.call(this, filepath);
};

console.log('🎯 LiveReload server is ready!');

// Handle graceful shutdown
process.on('SIGINT', () => {
  console.log('\n👋 Shutting down LiveReload server...');
  server.close();
  process.exit(0);
});
