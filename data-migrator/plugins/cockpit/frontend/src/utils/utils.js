/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Function to inject LiveReload script for development
export function injectLiveReload() {
  if (process.env.NODE_ENV !== 'production' && !document.querySelector('script[src*="livereload.js"]')) {
    const script = document.createElement('script');
    script.src = 'http://localhost:35729/livereload.js';
    script.async = true;
    document.head.appendChild(script);
  }
}
