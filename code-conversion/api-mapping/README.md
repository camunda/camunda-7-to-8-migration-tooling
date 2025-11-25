# Camunda 7 API to Camunda 8 API Mapping WebApp

Link to WebApp: [Camunda 7 API to Camunda 8 API Mapping WebApp](https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/).

This README covers how to maintain and extend the Camunda 7 API to Camunda 8 API Mapping WebApp. For a user-facing introduction, see the [project README](../README.md).

## Technical Details

The mapping tables are generated automatically from different sources:

-   The Camunda 7 OpenAPI specification
-   The Camunda 8 OpenAPI specification
-   A .jsx file that contains global configuration on a specific mapping
-   A collection of .jsx files that contain the mapping details for each endpoint group

These sources generate:

-   The endpoint group sections with headlines and tables
-   Endpoint information for Camunda 7 API endpoints including links to docs
-   Endpoint information for Camunda 8 API endpoints including links to docs if they are the "target" of a mapping
-   From the mapping details, tables are generated that contain the specific mappings between parameters or explanations on their discontinuation,... Colors are applied automatically, depending on the specified mapping details.

All navigation, filter and sorting options work without additional configuration.

## Where to Find the Resources

The OpenAPI specifications are located in the "openapi/camunda7" and "openapi/camunda8" directories. They are imported into the global configuration .jsx file of a specific mapping dynamically.

The .jsx files that pertain to a specific mapping are located under the "mappings" directory. Each of these global configuration files must be listed in the "mappingIndex.jsx" file found in the same directory. The "Select Mapping" navigation is generated dynamically from this file.

The mapping details for a specific mapping are stored in a subdirectory. In this directory, another index file is used to structure the mapping details by tag into separate files.

## Mapping Details

Each mapping is identified by the Camunda 7 endpoint path and operation. If there is a target Camunda 8 endpoint, it is also identified by path and operation. These two bits of information describe the first two columns of the mapping tables.

To describe the third column, there are six options to choose from. The first three options use text-based explanation to provide information about the endpoints. The key determines the color background of the table cell. You can only use one of the folloing three:

-   mappedExplanation (appears green): Use this key if there is a target Camunda 8 endpoint, but the mapping details have already been described elsewhere. Use text (e.g., `<p></p>`) to refer to the other row in the table.
-   roadmapExplanation (appears orange): Use this key if there is no target Camunda 8 endpoint yet. Use text (e.g., `<p></p>`) to provide details on when a Camunda 8 target endpoint can be expected.
-   discontinuedExplanation (appears red): Use this key if there is no target Camunda 8 endpoint, and there will likely be no target endpoint in the future. Use text (e.g., `<p></p>`) to explain why this Camunda 7 endpoint is redundant or discontinued.

The next three options describe parameter mapping tables. The key determines the color background and also the headers of these tables. The values following these keys have a fixed structure (typeScript might be a good idea): `rowInfo` is a list of objects with keys `leftEntry` and `rightEntry` to describe the left and right column of the parameter mapping tables. `additionalInfo` is a text-based (or whatever you want) addition that is added beneath the parameter mapping table. You can use all three keys at the same time:

-   direct (appears green): Use this key to describe parameter mappings that map one-to-one between Camunda 7 and Camunda 8, e.g., `processDefinitionId` and `processDefinitionkey`.
-   conceptual (appears orange): Use this key to describe parameter mappings that map conceptually between Camunda 7 and Camunda 8, e.g., starting a process instance and waiting for a synchronous response. Use this for parameters that might be added to a target endpoint in the future, e.g., the `businessKey`.
-   discontinued (appears red): Use this key to explain which parameters are discontinued in Camunda 8.
