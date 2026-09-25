/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.example.incident;

import java.util.Objects;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.camunda.bpm.engine.impl.incident.IncidentContext;

public final class EmailNotificationClient {

  protected final Properties notificationProperties;

  public EmailNotificationClient(Properties notificationProperties) {
    this.notificationProperties =
        Objects.requireNonNull(notificationProperties, "notificationProperties");
  }

  public void send(IncidentContext context, String message) {
    Properties mailProperties = new Properties();
    mailProperties.setProperty("mail.smtp.host", requiredValue("mail.smtp.host"));
    mailProperties.setProperty("mail.smtp.port", requiredValue("mail.smtp.port"));
    mailProperties.setProperty(
        "mail.smtp.starttls.enable", requiredValue("mail.smtp.starttls.enable"));

    try {
      Session session = Session.getInstance(mailProperties);
      MimeMessage notification = new MimeMessage(session);
      notification.setFrom(new InternetAddress(requiredValue("incident.notification.from")));
      for (String recipient : requiredValue("incident.notification.recipients").split(",")) {
        notification.addRecipient(
            Message.RecipientType.TO, new InternetAddress(recipient.trim()));
      }
      notification.setSubject("Camunda 7 failed job");
      notification.setText(formatMessage(context, message));
      Transport.send(notification);
    } catch (MessagingException exception) {
      throw new IllegalStateException("Cannot send failed-job notification.", exception);
    }
  }

  public String requiredValue(String key) {
    String value = notificationProperties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing notification setting: " + key);
    }
    return value.trim();
  }

  public String formatMessage(IncidentContext context, String message) {
    return "Process definition: "
        + context.getProcessDefinitionId()
        + "\nExecution: "
        + context.getExecutionId()
        + "\nActivity: "
        + context.getActivityId()
        + "\nJob definition: "
        + context.getJobDefinitionId()
        + "\nFailure: "
        + message;
  }
}
