package io.camunda.conversion.process_instance;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.AssignUserTaskResponse;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HandleUserTasks {

    @Autowired
    private CamundaClient camundaClient;

    public List<UserTask> searchUserTasksByBPMNModelIdentifier(String processDefinitionKey) {
        return camundaClient.newUserTaskSearchRequest()
                .filter(userTaskFilter -> userTaskFilter.bpmnProcessId(processDefinitionKey))
                .send()
                .join()
                .items();
    }

    public AssignUserTaskResponse claimUserTask(Long userTaskKey, String assignee) {
        return camundaClient.newUserTaskAssignCommand(userTaskKey)
                .assignee(assignee)
                .send()
                .join();
    }

    public CompleteUserTaskResponse completeUserTask(Long userTaskKey, Map<String, Object> variableMap) {
        return camundaClient.newUserTaskCompleteCommand(userTaskKey)
                .variables(variableMap)
                .send()
                .join();
    }

    public Variable getVariableFromTask(Long userTaskKey, String variableName) {
        return camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
                .filter(userTaskVariableFilter -> userTaskVariableFilter.name(variableName))
                .send()
                .join()
                .items()
                .get(0);
    }


}
