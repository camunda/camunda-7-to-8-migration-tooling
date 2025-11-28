/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import babel from "@rollup/plugin-babel";
import resolve from "@rollup/plugin-node-resolve";
import commonjs from "@rollup/plugin-commonjs";
import replace from "@rollup/plugin-replace";
import scss from "rollup-plugin-scss";
import copy from "rollup-plugin-copy";

export default {
  input: "src/plugin.js",
  output: {
    file: "dist/plugin.js"
  },
  plugins: [
    resolve(),
    babel({
      babelHelpers: "runtime",
      skipPreflightCheck: true,
      compact: true
    }),
    commonjs({
      include: "node_modules/**"
    }),
    replace({
      "process.env.NODE_ENV": JSON.stringify(process.env.NODE_ENV || "development"),
      preventAssignment: true
    }),
    scss({
      failOnError: true,
      fileName: 'plugin.css',
    }),
    copy({
      targets: [
        {
          src: 'dist/*',
          dest: '../target/classes/plugin-webapp/migrator-plugin/app'
        }
      ],
      hook: 'writeBundle',
      copyOnce: false,
      verbose: true
    })
  ]
};
