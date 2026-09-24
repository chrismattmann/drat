/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package drat.proteus.rest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

import junit.framework.TestCase;
import backend.RunMarker;

/** Tests repository statistics used by the Proteus progress display. */
public class TestServicesRestResource extends TestCase {

  private File temporaryDirectory;

  @Override
  protected void setUp() throws Exception {
    RunMarker.clear();
    temporaryDirectory = File.createTempFile("drat-repo-size", "");
    assertTrue(temporaryDirectory.delete());
    assertTrue(temporaryDirectory.mkdir());
  }

  @Override
  protected void tearDown() throws Exception {
    RunMarker.clear();
    delete(temporaryDirectory);
  }

  public void testExcludedNamesApplyToFilesAndDirectories() throws Exception {
    write(new File(temporaryDirectory, "source.txt"), "source");
    write(new File(temporaryDirectory, ".git"), "gitdir: elsewhere");
    File target = new File(temporaryDirectory, "target");
    assertTrue(target.mkdir());
    write(new File(target, "generated.txt"), "generated");

    Map<String, Long> size = new ServicesRestResource()
        .getRepositorySize(temporaryDirectory.getAbsolutePath(), ".git,target");

    assertEquals(Long.valueOf(1), size.get("numberOfFiles"));
    assertEquals(Long.valueOf(6), size.get("memorySize"));
  }

  public void testRepositoryTotalIsFrozenWhenRunBegins() throws Exception {
    write(new File(temporaryDirectory, "before.txt"), "before");
    RunMarker.write("crawl", "cli", temporaryDirectory.getAbsolutePath(),
        java.util.Arrays.asList(".git", "target"));
    write(new File(temporaryDirectory, "after.txt"), "after");

    Map<String, Long> size = new ServicesRestResource()
        .getRepositorySize(temporaryDirectory.getAbsolutePath(), ".git,target");

    assertEquals(Long.valueOf(1), size.get("numberOfFiles"));
  }

  private static void write(File file, String value) throws Exception {
    FileOutputStream stream = new FileOutputStream(file);
    try {
      stream.write(value.getBytes("UTF-8"));
    } finally {
      stream.close();
    }
  }

  private static void delete(File file) {
    if (file == null || !file.exists()) {
      return;
    }
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          delete(child);
        }
      }
    }
    assertTrue(file.delete());
  }
}
