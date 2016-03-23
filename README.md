Example Custom Reports for Activiti BPM Suite
====

This project provides an example report which displays some custom charts based on variables, for an example process.

Prerequisites
---

This project is built against Activiti Enterprise artifacts and assumes that you have access to these via Maven.

The provided pom file references the required Enterprise repositories on `artifacts.alfresco.com` but you will need to have some credentials configured for these in your `.m2/settings.xml` file. The repository IDs are `activiti-enterprise-releases` and `activiti-enterprise-snapshots`.

In order to run the tests and the Activiti BPM Suite WAR you will also need a valid Activiti license installed in the directory `$HOME/.activiti/enterprise-license`.

Building, Testing, Running
---
To build the JAR file via Maven

    mvn clean package

To run the Activiti BPM Suite with the JAR file customisations applied, use the `run-war` profile

    mvn clean install -Prun-war

