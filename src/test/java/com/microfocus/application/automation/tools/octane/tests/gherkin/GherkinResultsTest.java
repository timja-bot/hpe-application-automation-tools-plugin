/*
 * Certain versions of software and/or documents ("Material") accessible here may contain branding from
 * Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 * the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 * and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 * marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * (c) Copyright 2012-2023 Micro Focus or one of its affiliates.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ___________________________________________________________________
 */

package com.microfocus.application.automation.tools.octane.tests.gherkin;

import com.microfocus.application.automation.tools.octane.OctanePluginTestBase;
import com.microfocus.application.automation.tools.octane.actions.cucumber.CucumberTestResultsActionPublisher;
import com.microfocus.application.automation.tools.octane.tests.CopyResourceSCM;
import com.microfocus.application.automation.tools.octane.tests.TestUtils;
import hudson.matrix.*;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Maven;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.ToolInstallations;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by franksha on 05/01/2017.
 */
@SuppressWarnings({"squid:S2699","squid:S3658","squid:S2259","squid:S1872","squid:S2925","squid:S109","squid:S1607","squid:S2701","squid:S2698"})
public class GherkinResultsTest extends OctanePluginTestBase {

    private static String mavenName;


    private static Set<String> tests = new HashSet<>();
    static {
        tests.add("My Amazing Feature");
        tests.add("My Fancy Feature");
    }

    @BeforeClass
    public static void prepareClass() throws Exception {
        rule.jenkins.setNumExecutors(10);
        Maven.MavenInstallation mavenInstallation = ToolInstallations.configureMaven3();
        mavenName = mavenInstallation.getName();
    }

    @Test
    public void testGherkinResultsDirectlyOnWorkspace() throws Exception {
        gherkinResults("**/*Gherkin*.xml", true);
    }

    @Ignore("temp ignore")
    @Test
    public void testGherkinResultsDirectlyOnWorkspaceEmptyGlob() throws Exception {
        gherkinResults("", true);
    }

    @Test
    public void testGherkinResultsDirectlyOnWorkspaceNegative() throws Exception {
        gherkinResults("abcd.xml", false);
    }

    @Test
    public void testGherkinResultsInSubFolder() throws Exception {
        gherkinResultsInSubFolder("subFolder/*Gherkin*.xml", false);
    }

    @Test
    public void testGherkinResultsInSubFolderEmptyGlob() throws Exception {
        gherkinResultsInSubFolder("", true);
    }

    @Ignore("temp ignore")
    @Test
    public void testGherkinResultsInSubFolderNegative() throws Exception {
        gherkinResultsInSubFolder("*Gherkin*.xml", false);
    }

    @Ignore("temp ignore")
    @Test
    public void testGherkinResultsDirectlyOnWorkspaceLegacy() throws Exception {
        gherkinResultsLegacy("**/*Gherkin*.xml", true);
    }
    @Test
    public void testGherkinResultsDirectlyOnWorkspaceLegacyEmptyGlob() throws Exception {
        gherkinResultsLegacy("", true);
    }

    @Ignore("temp ignore")
    @Test
    public void testGherkinResultsDirectlyOnWorkspaceLegacyNegative() throws Exception {
        gherkinResultsLegacy("abcd.xml", false);
    }

    @Test
    public void testGherkinResultsInSubFolderLegacy() throws Exception {
        gherkinResultsLegacyWithSubFolder("subFolder/*Gherkin*.xml", false);
    }

    @Test
    public void testGherkinResultsInSubFolderLegacyEmptyGlob() throws Exception {
        gherkinResultsLegacyWithSubFolder("", true);
    }

    @Test
    public void testGherkinResultsInSubFolderLegacyNegative() throws Exception {
        gherkinResultsLegacyWithSubFolder("*Gherkin*.xml", false);
    }

    @Test
    public void testGherkinResultsMatrixProject() throws Exception {
        String projectName = "root-job-" + UUID.randomUUID().toString();
        MatrixProject matrixProject = rule.createProject(MatrixProject.class, projectName);
        matrixProject.setAxes(new AxisList(new Axis("osType", "Linux", "Windows")));

        matrixProject.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
                TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, null, null, "-Dmaven.test.failure.ignore=true"));
        matrixProject.getPublishersList().add(new CucumberTestResultsActionPublisher(""));
        matrixProject.setScm(new CopyResourceSCM("/helloCucumberWorld"));

        MatrixBuild build = (MatrixBuild) TestUtils.runAndCheckBuild(matrixProject);
        for (MatrixRun run : build.getExactRuns()) {
            assertTestResultsEqual(tests, new File(run.getRootDir(), "mqmTests.xml"));
        }
//        Assert.assertEquals(new HashSet<>(Arrays.asList(projectName + "/osType=Windows#1", projectName + "/osType=Linux#1")), getQueuedItems());
        Assert.assertFalse(new File(build.getRootDir(), "mqmTests.xml").exists());
    }

    //@Test
    public void testGherkinResultsWrongFile() throws Exception {
        gherkinResults("pom.xml", false);
    }

    //@Test
    public void testGherkinResultsWrongLongFile() throws Exception {
        gherkinResults("settings.xml", false);
    }

    private void gherkinResults(String glob, boolean buildShouldSucceed) throws Exception {
        String projectName = "root-job-" + UUID.randomUUID().toString();
        FreeStyleProject project = rule.createFreeStyleProject(projectName);

        project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
                TestUtils.getMavenHome(), System.getenv("TEMP")), mavenName, null, null, "-Dmaven.test.failure.ignore=true"));
        project.setScm(new CopyResourceSCM("/helloCucumberWorld"));

        project.getPublishersList().add(new CucumberTestResultsActionPublisher(glob));

        assertProject(project, buildShouldSucceed);
    }

    private void gherkinResultsInSubFolder(String glob, boolean buildShouldSucceed) throws Exception {
        String projectName = "root-job-" + UUID.randomUUID().toString();
        FreeStyleProject project = rule.createFreeStyleProject(projectName);

        project.getBuildersList().add(new Maven(String.format("--settings \"%s\\conf\\settings.xml\" clean test -Dmaven.repo.local=%s\\m2-temp",
                TestUtils.getMavenHome(),System.getenv("TEMP")), mavenName, "subFolder/pom.xml", null, "-Dmaven.test.failure.ignore=true"));
        project.setScm(new CopyResourceSCM("helloCucumberWorld", "subFolder"));

        project.getPublishersList().add(new CucumberTestResultsActionPublisher(glob));

        assertProject(project, buildShouldSucceed);
    }

    private void gherkinResultsLegacy(String glob, boolean buildShouldSucceed) throws Exception {
        MavenModuleSet project = prepareLegacyProject(false);
        project.getPublishersList().add(new CucumberTestResultsActionPublisher(glob));
        assertProject(project, buildShouldSucceed);
    }

    private void gherkinResultsLegacyWithSubFolder(String glob, boolean buildShouldSucceed) throws Exception {
        MavenModuleSet project = prepareLegacyProject(true);
        project.getPublishersList().add(new CucumberTestResultsActionPublisher(glob));
        assertProject(project, buildShouldSucceed);
    }

    private MavenModuleSet prepareLegacyProject(boolean subfolder) throws IOException {
        String projectName = "root-job-" + UUID.randomUUID().toString();
        MavenModuleSet project = rule.createProject(MavenModuleSet.class, projectName);
        project.runHeadless();

        project.setMaven(mavenName);
        project.setGoals(String.format("clean test --settings \"%s\\conf\\settings.xml\" -Dmaven.repo.local=%s\\m2-temp -Dmaven.test.failure.ignore=true",
                TestUtils.getMavenHome(),System.getenv("TEMP")));
        if(subfolder) {
            project.setRootPOM("subFolder/pom.xml");
            project.setScm(new CopyResourceSCM("/helloCucumberWorld", "subFolder"));
        } else {
            project.setScm(new CopyResourceSCM("/helloCucumberWorld"));
        }
        return project;
    }

    private void assertProject(AbstractProject project, boolean buildShouldSucceed) throws Exception {
        if(buildShouldSucceed) {
            AbstractBuild build = TestUtils.runAndCheckBuild(project);
            assertTestResultsEqual(tests, new File(build.getRootDir(), "mqmTests.xml"));
//            Assert.assertEquals(Collections.singleton(project.getName() + "#1"), getQueuedItems());
        } else {
            AbstractBuild build = (AbstractBuild) project.scheduleBuild2(0).get();
            Assert.assertEquals("Build should fail", Result.FAILURE, build.getResult());
//            Assert.assertEquals("Expects empty queue", 0, getQueuedItems().size());
        }
    }

    private void assertTestResultsEqual(Set<String> expected, File actual) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document actualDoc = dBuilder.parse(actual);

        NodeList actualTests = actualDoc.getElementsByTagName("gherkin_test_run");
        Assert.assertEquals("Number of tests should be equal", expected.size(), actualTests.getLength());

        NodeList automatedTests = actualDoc.getElementsByTagName("test_run");
        Assert.assertEquals("There should be no automated tests", 0, automatedTests.getLength());

        int i=0;
        for (String expectedName : expected) {
            Node actualTest = actualTests.item(i++);
            Assert.assertEquals("Test name should be equal", expectedName, getAttr(actualTest, "name"));
            Assert.assertEquals("Test status should be equal", "Passed", getAttr(actualTest, "status"));
        }
    }

    private String getAttr(Node node, String attrName) {
        return node.getAttributes().getNamedItem(attrName).getNodeValue();
    }
}
