Example Custom Reports for Activiti BPM Suite
====

This project provides an example report which displays some custom charts based on variables, for an example process.

Prerequisites
---

This project is built against Activiti Enterprise artifacts and assumes that you have configured access to these in your `.m2/settings.xml` file.

The provided pom file defines the repositories on `artifacts.alfresco.com` but you will need to have some credentials configured for these.

Building, Testing, Running
---
To build the JAR file via Maven

    mvn clean package

To run the Activiti BPM Suite with the JAR file customisations applied, use the `run-war` profile

    mvn clean install -Prun-war

