/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useMemo, useState} from "react";
import {createColumnHelper} from '@tanstack/react-table';
import PaginatedTable from "./PaginatedTable";
import {ENTITY_TYPES} from "./utils/types";
import {injectLiveReload} from "./utils/utils";

function SkippedEntities({camundaAPI}) {
  const [skippedEntities, setSkippedEntities] = useState([]);
  const [selectedType, setSelectedType] = useState(ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE);
  const [loading, setLoading] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const [processInstanceIds, setProcessInstanceIds] = useState({});
  const [processDefinitionKeys, setProcessDefinitionKeys] = useState({});
  const [variableMetadata, setVariableMetadata] = useState({}); // New state for variable name and type
  const [showSkipped, setShowSkipped] = useState(true);
  const [viewMode, setViewMode] = useState('runtime'); // New state for runtime/history mode

  const cockpitApi = camundaAPI.cockpitApi;
  const engine = camundaAPI.engine;
  const restApi = '/engine-rest';

  const getEntityLink = (entity) => {
    if (entity.type === ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE) {
      return <a href={`#/process-instance/${entity.c7Id}/runtime`}>{entity.c7Id}</a>;
    } else if (entity.type === ENTITY_TYPES.HISTORY_PROCESS_INSTANCE) {
      return <a href={`#/process-instance/${entity.c7Id}/history`}>{entity.c7Id}</a>;
    } else {
      return entity.c7Id;
    }
  }

  const getColumnHeader = () => {
    if (viewMode === 'runtime') {
      return 'Process Instance ID';
    } else {
      // For history mode, extract the entity type from the selectedType
      const entityTypeMap = {
        HISTORY_PROCESS_DEFINITION: "Process Definition ID",
        HISTORY_PROCESS_INSTANCE: "Process Instance ID",
        HISTORY_INCIDENT: "Incident ID",
        HISTORY_VARIABLE: "Variable ID",
        HISTORY_USER_TASK: "User Task ID",
        HISTORY_FLOW_NODE: "Flow Node ID",
        HISTORY_DECISION_INSTANCE: "Decision Instance ID",
        HISTORY_DECISION_DEFINITION: "Decision Definition ID"
      };
      return entityTypeMap[selectedType] || 'ID';
    }
  };

  // Function to render skip reason with links for flow node references
  const renderSkipReasonWithLinks = (skipReason, entity) => {
    // Regular expression to match "flow node with id [activityId]" pattern
    const flowNodeRegex = /flow node with id \[([^\]]+)]/g;

    // Split the text and process each part
    const parts = [];
    let lastIndex = 0;
    let match;

    while ((match = flowNodeRegex.exec(skipReason)) !== null) {
      // Add text before the match
      if (match.index > lastIndex) {
        parts.push(skipReason.substring(lastIndex, match.index));
      }

      // Extract activity ID from the match
      const activityId = match[1];

      // Create link to history process instance view with activity highlighting
      const processInstanceId = entity.type === ENTITY_TYPES.HISTORY_VARIABLE
        ? processInstanceIds[entity.c7Id]
        : entity.c7Id;

      if (processInstanceId) {
        parts.push("flow node with id ");
        parts.push(
          <a
            key={match.index}
            href={`#/process-instance/${processInstanceId}/history?activityIds=${encodeURIComponent(activityId)}`}
          >
            {activityId}
          </a>
        );
      } else {
        parts.push(match[0]);
      }

      lastIndex = match.index + match[0].length;
    }

    // Add remaining text after the last match
    if (lastIndex < skipReason?.length) {
      parts.push(skipReason.substring(lastIndex));
    }

    return parts.length > 1 ? <>{parts}</> : skipReason;
  };

  const columnHelper = createColumnHelper();

  const columns = useMemo(
    () => {
      let baseColumns = [
        columnHelper.accessor('c7Id', {
          header: getColumnHeader(),
          cell: info => getEntityLink(info.row.original),
          size: 370
        })];

      if (!showSkipped) {
        baseColumns.push(columnHelper.accessor('c8Key', {
          header: 'C8 Key',
          cell: info =>
            <a href={`http://localhost:8080/operate/processes/${info.getValue()}`} target={"_blank"}>{info.getValue()}</a>,
        }));
      }

      // Only add skip reason column when showing skipped entities
      if (showSkipped) {
        baseColumns = [...baseColumns,
          columnHelper.accessor('skipReason', {
            header: 'Skip reason',
            cell: info => renderSkipReasonWithLinks(info.getValue(), info.row.original),
          }),
        ];
      }

      // Add process instance column for HISTORY_VARIABLE type
      if (selectedType === ENTITY_TYPES.HISTORY_VARIABLE) {
        baseColumns.splice(1, 0,
          columnHelper.accessor('c7Id', {
            id: 'processInstanceId',
            header: 'Process Instance ID',
            cell: info => {
              const variableId = info.getValue();
              const processInstanceId = processInstanceIds[variableId];

              return processInstanceId ?
                <a href={`#/process-instance/${processInstanceId}/history`}>{processInstanceId}</a> :
                <span>Loading...</span>;
            },
          }),
          columnHelper.accessor('name', {
            header: 'Variable Name',
            cell: info => {
              const variableId = info.row.original.c7Id;
              return variableMetadata[variableId]?.name || <span>Loading...</span>;
            },
          }),
          columnHelper.accessor('type', {
            header: 'Variable Type',
            cell: info => {
              const variableId = info.row.original.c7Id;
              return variableMetadata[variableId]?.type || <span>Loading...</span>;
            },
          })
        );
      }

      // Add process definition key column for RUNTIME_PROCESS_INSTANCE type
      if (selectedType === ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE) {
        baseColumns.splice(1, 0,
          columnHelper.accessor('c7Id', {
            id: 'processDefinitionKey',
            header: 'Process Definition Key',
            cell: info => {
              const processInstanceId = info.getValue();
              const definitionKey = processDefinitionKeys[processInstanceId];

              return definitionKey || <span>Loading...</span>;
            },
          })
        );
      }

      // Add process definition key column for HISTORY_PROCESS_INSTANCE type
      if (selectedType === ENTITY_TYPES.HISTORY_PROCESS_INSTANCE) {
        baseColumns.splice(1, 0,
          columnHelper.accessor('c7Id', {
            id: 'historyProcessDefinitionKey',
            header: 'Process Definition Key',
            cell: info => {
              const processInstanceId = info.getValue();
              const definitionKey = processDefinitionKeys[processInstanceId];

              return definitionKey || <span>Loading...</span>;
            },
          })
        );
      }

      return baseColumns;
    },
    [selectedType, processInstanceIds, showSkipped, viewMode, processDefinitionKeys, variableMetadata]
  );

  const getEntityTypeLabel = (entityType) => {
    const labels = {
      HISTORY_PROCESS_DEFINITION: "Process Definition",
      HISTORY_PROCESS_INSTANCE: "Process Instance",
      HISTORY_INCIDENT: "Incident",
      HISTORY_VARIABLE: "Variable",
      HISTORY_USER_TASK: "User Task",
      HISTORY_FLOW_NODE: "Flow Node",
      HISTORY_DECISION_INSTANCE: "Decision Instance",
      HISTORY_DECISION_DEFINITION: "Decision Definition",
      RUNTIME_PROCESS_INSTANCE: "Process Instance"
    };
    return labels[entityType] || entityType;
  };

  // Filter entity types based on view mode
  const getAvailableEntityTypes = () => {
    if (viewMode === 'runtime') {
      return {RUNTIME_PROCESS_INSTANCE: ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE};
    } else {
      // Return all history types
      const historyTypes = {};
      Object.entries(ENTITY_TYPES).forEach(([key, value]) => {
        if (key.startsWith('HISTORY_')) {
          historyTypes[key] = value;
        }
      });
      return historyTypes;
    }
  };

  const fetchTotalCount = async () => {
    try {
      const endpoint = showSkipped ? 'skipped' : 'migrated';
      const response = await fetch(
        `${cockpitApi}/plugin/migrator-plugin/${engine}/migrator/${endpoint}/count?type=${selectedType}`,
        {
          headers: {
            'Accept': 'text/plain'
          }
        }
      );
      const count = await response.json();
      setTotalCount(typeof count === 'number' ? count : count.total || count.count || 0);
    } catch (err) {
      console.error('Failed to fetch total count:', err);
      setTotalCount(0);
    }
  };

  const fetchData = async (pageIndex = 0, pageSize = 10) => {
    setLoading(true);
    try {
      // If totalCount is 0, short-circuit and set empty array
      if (totalCount === 0) {
        setSkippedEntities([]);
        return;
      }

      const offset = pageIndex * pageSize;
      const endpoint = showSkipped ? 'skipped' : 'migrated';
      const response = await fetch(
        `${cockpitApi}/plugin/migrator-plugin/${engine}/migrator/${endpoint}?type=${selectedType}&offset=${offset}&limit=${pageSize}`,
        {
          headers: {
            'Accept': 'application/json'
          }
        }
      );
      const data = await response.json();
      const entities = Array.isArray(data) ? data : data.items || [];


      setSkippedEntities(entities);

      // Fetch process instance IDs for HISTORY_VARIABLE entities
      if (selectedType === ENTITY_TYPES.HISTORY_VARIABLE && entities.length > 0) {
        fetchProcessInstanceIds(entities);
      }

      // Fetch process definition keys for process instances
      if ((selectedType === ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE || selectedType === ENTITY_TYPES.HISTORY_PROCESS_INSTANCE) && entities.length > 0) {
        fetchProcessDefinitionKeys(entities);
      }
    } catch (err) {
      console.error(err);
      setSkippedEntities([]);
    } finally {
      setLoading(false);
    }
  };

  // Fetch process instance IDs for HISTORY_VARIABLE entities
  const fetchProcessInstanceIds = async (entities) => {
    const newProcessInstanceIds = {...processInstanceIds};
    let hasChanges = false;

    const fetchPromises = entities.map(async (entity) => {
      // Skip if we already have the process instance ID
      if (newProcessInstanceIds[entity.c7Id]) return;

      try {
        const response = await fetch(
          `${restApi}/history/variable-instance/${entity.c7Id}`,
          {
            headers: {
              'Accept': 'application/json'
            }
          }
        );
        const data = await response.json();

        if (data && data.processInstanceId) {
          newProcessInstanceIds[entity.c7Id] = data.processInstanceId;
          hasChanges = true;
        }

        // Also populate the name and type from the variable instance data
        if (data && data.name && data.type) {
          // entity.name = data.name;
          // entity.type = data.type;

          // Store variable metadata separately
          setVariableMetadata(prevMetadata => ({
            ...prevMetadata,
            [entity.c7Id]: {name: data.name, type: data.type}
          }));
        }
      } catch (err) {
        console.error(`Failed to fetch process instance ID for variable ${entity.c7Id}:`, err);
      }
    });

    await Promise.all(fetchPromises);

    if (hasChanges) {
      // Force update with a new object reference to ensure React detects the change
      setProcessInstanceIds({...newProcessInstanceIds});
    }
  };

  // Fetch process definition keys for process instances
  const fetchProcessDefinitionKeys = async (entities) => {
    const newProcessDefinitionKeys = {...processDefinitionKeys};
    let hasChanges = false;

    const fetchPromises = entities.map(async (entity) => {
      // Skip if we already have the process definition key
      if (newProcessDefinitionKeys[entity.c7Id]) return;

      try {
        let response;

        // Use different endpoints for runtime vs history
        if (selectedType === ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE) {
          response = await fetch(
            `${restApi}/process-instance/${entity.c7Id}`,
            {
              headers: {
                'Accept': 'application/json'
              }
            }
          );
        } else if (selectedType === ENTITY_TYPES.HISTORY_PROCESS_INSTANCE) {
          response = await fetch(
            `${restApi}/history/process-instance/${entity.c7Id}`,
            {
              headers: {
                'Accept': 'application/json'
              }
            }
          );
        }

        if (!response.ok) {
          console.error(`Failed to fetch process instance ${entity.c7Id}:`, response.status, response.statusText);
          newProcessDefinitionKeys[entity.c7Id] = 'Error';
          hasChanges = true;
          return;
        }

        const data = await response.json();

        if (data && (data.processDefinitionKey || data.definitionKey)) {
          newProcessDefinitionKeys[entity.c7Id] = data.processDefinitionKey || data.definitionKey;
          hasChanges = true;
        } else {
          console.warn('No processDefinitionKey found for process instance:', entity.c7Id, 'Available fields:', Object.keys(data || {}));
          newProcessDefinitionKeys[entity.c7Id] = 'Unknown';
          hasChanges = true;
        }
      } catch (err) {
        console.error(`Failed to fetch process definition key for process instance ${entity.c7Id}:`, err);
        newProcessDefinitionKeys[entity.c7Id] = 'Error';
        hasChanges = true;
      }
    });

    await Promise.all(fetchPromises);

    if (hasChanges) {
      setProcessDefinitionKeys({...newProcessDefinitionKeys});
    }
  };

  // Initial data load and when entity type changes
  useEffect(() => {
    // Inject LiveReload for development
    injectLiveReload();

    // Reset process instance IDs when changing entity type
    if (selectedType !== ENTITY_TYPES.HISTORY_VARIABLE) {
      setProcessInstanceIds({});
      setVariableMetadata({}); // Also reset variable metadata
    }

    // Reset process definition keys when changing entity type
    if (selectedType !== ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE && selectedType !== ENTITY_TYPES.HISTORY_PROCESS_INSTANCE) {
      setProcessDefinitionKeys({});
    }

    // Reset variable metadata when switching between skipped/migrated modes for variables
    if (selectedType === ENTITY_TYPES.HISTORY_VARIABLE) {
      setVariableMetadata({});
    }

    // When switching to runtime mode, set entity type to runtime process instance
    if (viewMode === 'runtime') {
      setSelectedType(ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE);
    } else if (viewMode === 'history' && selectedType === ENTITY_TYPES.RUNTIME_PROCESS_INSTANCE) {
      // When switching to history mode from runtime, default to history process instance
      setSelectedType(ENTITY_TYPES.HISTORY_PROCESS_INSTANCE);
    }

    // Fetch total count first, then fetch data
    fetchTotalCount().then(() => {
      fetchData(0, 10);
    });
  }, [selectedType, showSkipped, viewMode]); // Add viewMode to dependency array

  // Handle pagination changes
  const handlePageChange = (pageIndex, pageSize) => {
    fetchData(pageIndex, pageSize);
  };

  if (loading && skippedEntities.length === 0) {
    return <div>Loading...</div>;
  }

  return (
    <>
      <section>
        <div className="inner">
          <header>
            <h1 style={{float: 'left'}} className="section-title">Camunda 7 to 8 Data Migrator</h1>

            <div style={{marginTop: '5px', float: 'right'}}>
              <label style={{marginRight: '20px'}}>
                <input
                  type="radio"
                  value="skipped"
                  checked={showSkipped}
                  onChange={() => setShowSkipped(true)}
                  style={{marginRight: '5px'}}
                />
                Skipped
              </label>
              <label>
                <input
                  type="radio"
                  value="migrated"
                  checked={!showSkipped}
                  onChange={() => setShowSkipped(false)}
                  style={{marginRight: '5px'}}
                />
                Migrated
              </label>
              <label style={{marginRight: '40px'}}>
              </label>
              <label style={{marginRight: '20px'}}>
                <input
                  type="radio"
                  value="runtime"
                  checked={viewMode === 'runtime'}
                  onChange={() => setViewMode('runtime')}
                  style={{marginRight: '5px'}}
                />
                Runtime
              </label>
              <label>
                <input
                  type="radio"
                  value="history"
                  checked={viewMode === 'history'}
                  onChange={() => setViewMode('history')}
                  style={{marginRight: '5px'}}
                />
                History
              </label>
            </div>
          </header>

          <div style={{marginBottom: '20px'}}>
            {viewMode === 'history' && (
              <div>
                <label htmlFor="type-selector" style={{marginRight: '10px'}}>
                  Type:
                </label>
                <select
                  id="type-selector"
                  value={selectedType}
                  onChange={(e) => setSelectedType(e.target.value)}
                  style={{padding: '5px', minWidth: '200px'}}
                >
                  {Object.entries(getAvailableEntityTypes()).map(([key, value]) => (
                    <option key={key} value={value}>
                      {getEntityTypeLabel(value)}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          <PaginatedTable
            columns={columns}
            data={skippedEntities}
            totalCount={totalCount}
            loading={loading}
            onPageChange={handlePageChange}
            initialPageSize={10}
            pageSizeOptions={[5, 10, 20, 30, 40, 50]}
          />
        </div>
      </section>
    </>
  );
}

export default SkippedEntities;
