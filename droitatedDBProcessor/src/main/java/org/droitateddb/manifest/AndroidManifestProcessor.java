/*
 * Copyright (C) 2014 The droitated DB Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.droitateddb.manifest;

import org.droitateddb.processor.ContentProviderData;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Allows parsing and changing data of the AndroidManifest.xml
 *
 * @author Alexander Frank
 * @author Falk Appel
 */
public class AndroidManifestProcessor {

	private final File   manifestFile;
	private final String generatedPackage;

	public AndroidManifestProcessor(final File manifestFile, final String generatedPackage) {
		this.manifestFile = manifestFile;
		this.generatedPackage = generatedPackage;
	}

	public AndroidManifest parse() throws Exception {
		InputStream manifestStream = new FileInputStream(manifestFile);

		try {
			Document document = createDocument(manifestStream);
			Element root = document.getDocumentElement();
			String usedPackage = root.getAttribute("package");

			return new AndroidManifest(usedPackage, findExistingContentProvider(root));
		} finally {
			manifestStream.close();
		}
	}

	private List<ContentProviderData> findExistingContentProvider(final Element root) {
		NodeList nodesUnderApplication = getApplicationNodeFromRoot(root).getChildNodes();

		List<ContentProviderData> data = new ArrayList<ContentProviderData>(nodesUnderApplication.getLength());

		for (int i = 0; i < nodesUnderApplication.getLength(); i++) {
			Node node = nodesUnderApplication.item(i);
			if (node.getNodeName().equals("provider")) {
				NamedNodeMap attributes = node.getAttributes();
				String providerName = attributes.getNamedItem("android:name").getTextContent();
				String authority = attributes.getNamedItem("android:authorities").getTextContent();
				Node exportedAttribute = attributes.getNamedItem("android:exported");
				boolean exported = false;
				if (exportedAttribute != null) {
					exported = Boolean.parseBoolean(exportedAttribute.getTextContent());
				}
				data.add(new ContentProviderData(providerName, authority, exported));
			}
		}
		return data;
	}

    private static Document createDocument(final InputStream manifestStream) throws Exception {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder.parse(manifestStream);
    }

	private static Node getApplicationNodeFromRoot(final Element root) {
		NodeList applicationTag = root.getElementsByTagName("application");
		if (applicationTag.getLength() != 1) {
			throw new IllegalStateException(
					"Wrong number of application tags found within AndroidManifest.xml. There are " + applicationTag.getLength() + " tags");
		}
		return applicationTag.item(0);
	}

    public ManifestBuilder change() throws Exception {
        return new ManifestBuilder(manifestFile, parse(),generatedPackage);
    }

	public static final class ManifestBuilder {

		private final File                            manifestFile;
		private final LinkedList<ContentProviderData> addedProviders;
		private final AndroidManifest                 androidManifest;
		private final String                          generatedPackage;

		private ManifestBuilder(final File manifestFile, final AndroidManifest androidManifest, final String generatedPackage) {
			this.manifestFile = manifestFile;
			this.androidManifest = androidManifest;
			this.generatedPackage = generatedPackage;
			addedProviders = new LinkedList<ContentProviderData>();
		}

		public ManifestBuilder addProviderIfNotExists(final ContentProviderData contentProviderData) {
			addedProviders.add(contentProviderData);
			return this;
		}

		public void commit() throws Exception {
			Document document = createDocument(new FileInputStream(manifestFile));
			Element root = document.getDocumentElement();
			Node application = getApplicationNodeFromRoot(root);
			boolean addedSomething = addProviderTagsIfNeeded(document, application);
			boolean removedSomething = removeUnusedProviders(application);
			saveToAndroidManifest(document, addedSomething || removedSomething);
		}

		private boolean addProviderTagsIfNeeded(final Document document, final Node application) {
			boolean changesSomething = false;
			for (ContentProviderData added : addedProviders) {
				if (notContainedInProviderList(added, androidManifest.getContentProviders())) {
					Element newProvider = document.createElement("provider");

					newProvider.setAttribute("android:name", added.getCanonicalName());
					newProvider.setAttribute("android:authorities", added.getAuthority());
					newProvider.setAttribute("android:exported", Boolean.toString(added.isExported()));
					newProvider.setAttribute("droitateddb:generated", "true");
					application.appendChild(newProvider);

					document.getDocumentElement().setAttribute("xmlns:droitateddb", "http://droitateddb.org");
					changesSomething = true;
				}
			}
			return changesSomething;
		}

		private boolean removeUnusedProviders(final Node application) {
			boolean changedSomething = false;
			for (ContentProviderData inManifest : androidManifest.getContentProviders()) {
				if (notContainedInProviderList(inManifest, addedProviders)) {
					changedSomething |= removeSingleProvider(inManifest, application);
				}
			}
			return changedSomething;
		}

		private boolean removeSingleProvider(final ContentProviderData inManifest, final Node application) {
			NodeList applicationChildren = application.getChildNodes();
			for (int i = 0; i < applicationChildren.getLength(); i++) {
				Node child = applicationChildren.item(i);
				if (child.getNodeName().equals("provider") && isGenerated(child)) {
					NamedNodeMap attributes = child.getAttributes();
					Node name = attributes.getNamedItem("android:name");
					Node authority = attributes.getNamedItem("android:authorities");
					if (authority.getTextContent().equals(inManifest.getAuthority()) && namesAreEqual(name.getTextContent(), inManifest.getCanonicalName())) {
						application.removeChild(child);
						return true;
					}
				}
			}
			return false;
		}

		private boolean notContainedInProviderList(final ContentProviderData added, final List<ContentProviderData> providers) {
			for (ContentProviderData inManifest : providers) {
				if (inManifest.getAuthority().equals(added.getAuthority()) && namesAreEqual(inManifest.getCanonicalName(), added.getCanonicalName()) &&
						inManifest.isExported() == added.isExported()) {
					return false;
				}
			}
			return true;
		}

		private boolean namesAreEqual(final String providerName, final String otherProviderName) {
			String comparableManifestName = packagedProviderName(providerName);
			String comparableProviderName = packagedProviderName(otherProviderName);
			return comparableManifestName.equals(comparableProviderName);
		}

		private String packagedProviderName(final String name) {
			String packagedName;
			if (name.startsWith(".")) {
				packagedName = generatedPackage + name;
			} else {
				packagedName = name;
			}
			return packagedName;
		}

		private boolean isGenerated(final Node child) {
			NamedNodeMap attributes = child.getAttributes();
			Node generated = attributes.getNamedItem("droitateddb:generated");
			if (generated == null) {
				return false;
			}
			return Boolean.parseBoolean(generated.getTextContent());
		}

		private void saveToAndroidManifest(final Document document, final boolean changedSomething) throws TransformerFactoryConfigurationError,
																										   TransformerConfigurationException,
																										   FileNotFoundException, TransformerException {
			if (changedSomething) {

				DOMSource source = new DOMSource(document);

				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				StreamResult result = new StreamResult(new FileOutputStream(manifestFile));
				transformer.transform(source, result);
			}
		}
	}

}
