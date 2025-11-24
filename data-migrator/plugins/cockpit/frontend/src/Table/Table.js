/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from "react";

import "./Table.scss";

export default function Table({ head, children }) {
  return (
    <table className="Table">
      <thead>
        <tr>{head}</tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  );
}

Table.Head = function Head({ children, style }) {
  return <th className="TableHead" style={style}>{children}</th>;
};

Table.Row = function Row({ children }) {
  return <tr className="TableRow">{children}</tr>;
};

Table.Cell = function Cell({ children, style, colSpan }) {
  return <td className="TableCell" style={style} colSpan={colSpan}>{children}</td>;
};
