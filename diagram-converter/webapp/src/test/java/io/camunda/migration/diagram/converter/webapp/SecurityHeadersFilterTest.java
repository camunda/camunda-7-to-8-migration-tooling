/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.diagram.converter.webapp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

  @Test
  void addsHeadersToErrorDispatches() throws IOException, ServletException {
    var request = new MockHttpServletRequest();
    request.setDispatcherType(DispatcherType.ERROR);
    var response = new MockHttpServletResponse();

    new SecurityHeadersFilter().doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader("Content-Security-Policy"))
        .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
  }
}
