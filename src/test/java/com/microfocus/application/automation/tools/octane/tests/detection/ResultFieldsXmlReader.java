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

package com.microfocus.application.automation.tools.octane.tests.detection;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;

public class ResultFieldsXmlReader {

    private Reader input;
    private XMLEventReader eventReader;
    private ResultFields resultFields;
    private List<TestAttributes> testAttributes;

    public ResultFieldsXmlReader(Reader input) throws XMLStreamException {
        this.input = input;
        eventReader = XMLInputFactory.newInstance().createXMLEventReader(input);
        resultFields = new ResultFields();
        testAttributes = new LinkedList<TestAttributes>();
    }

    public TestResultContainer readXml() {
        boolean fieldsElement = false;
        try {
            while (eventReader.hasNext()) {
                XMLEvent event = eventReader.nextEvent();
                if (event instanceof StartElement) {
                    StartElement element = (StartElement) event;
                    String localName = element.getName().getLocalPart();
                    if ("test_fields".equals(localName)) {
                        fieldsElement = true;
                    }
                    if ("test_field".equals(localName)) {
                        if (!fieldsElement) {
                            Assert.fail("<test_field> element found, but surrounding element '<test_fields>' is missing in the XML file");
                        }
                        String type = element.getAttributeByName(new QName("type")).getValue();
                        String value = element.getAttributeByName(new QName("value")).getValue();
                        if (type.equals("Framework")) {
                            resultFields.setFramework(value);
                        } else if (type.equals("Testing_Tool_Type")) {
                            resultFields.setTestingTool(value);
                        } else if (type.equals("Test_Level")) {
                            resultFields.setTestLevel(value);
                        }
                    }
                    if ("test_run".equals(localName)) {
                        String moduleName = element.getAttributeByName(new QName("module")).getValue();
                        String packageName = element.getAttributeByName(new QName("package")).getValue();
                        String className = element.getAttributeByName(new QName("class")).getValue();
                        String testName = element.getAttributeByName(new QName("name")).getValue();
                        testAttributes.add(new TestAttributes(moduleName, packageName, className, testName));
                    }
                }
            }
            IOUtils.closeQuietly(input);
            eventReader.close();
        } catch (XMLStreamException e){
            throw new RuntimeException(e);
        }
        return new TestResultContainer(testAttributes, resultFields);
    }

    public class TestResultContainer {

        private List<TestAttributes> testAttributes;
        private ResultFields resultFields;

        public TestResultContainer(List<TestAttributes> testAttributes, ResultFields resultFields) {
            this.testAttributes = testAttributes;
            this.resultFields = resultFields;
        }

        public List<TestAttributes> getTestAttributes() {
            return testAttributes;
        }

        public ResultFields getResultFields() {
            return resultFields;
        }
    }

    public class TestAttributes {
        private final String moduleName;
        private final String packageName;
        private final String className;
        private final String testName;

        public TestAttributes(final String moduleName, final String packageName, final String className, final String testName) {
            this.moduleName = moduleName;
            this.packageName = packageName;
            this.className = className;
            this.testName = testName;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getModuleName() {
            return moduleName;
        }

        public String getClassName() {
            return className;
        }

        public String getTestName() {
            return testName;
        }
    }
}
