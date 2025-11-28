/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { execSync } from 'child_process';

/**
 * Global teardown that runs after all tests.
 * Stops and removes Docker Compose containers to clean up resources.
 */
async function globalTeardown() {
  console.log('Global Teardown: Stopping Docker Compose services...');

  try {
    // Stop and remove containers, networks
    execSync('docker compose down -v', {
      encoding: 'utf-8',
      stdio: 'inherit'
    });
    console.log('Global Teardown: ✓ Docker Compose services stopped and cleaned up.');
  } catch (error) {
    console.error('Global Teardown: Error stopping Docker Compose:', error);
    // Don't throw - we want teardown to complete even if there's an error
  }
}

export default globalTeardown;

