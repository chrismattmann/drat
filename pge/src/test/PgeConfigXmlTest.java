/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import org.junit.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PgeConfigXmlTest {

  @Test
  public void allPgeConfigurationFilesAreWellFormedXml() throws Exception {
    File configDirectory = new File("src/main/resources/config");
    File[] configs = configDirectory.listFiles((directory, name) ->
        name.endsWith(".xml"));

    assertNotNull("Unable to list PGE configuration files", configs);
    assertTrue("No PGE configuration files found", configs.length > 0);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    for (File config : configs) {
      factory.newDocumentBuilder().parse(config);
    }
  }
}
