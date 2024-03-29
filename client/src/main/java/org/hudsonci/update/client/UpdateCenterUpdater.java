/*******************************************************************************
 *
 * Copyright (c) 2011 Oracle Corporation.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *
 *    Winston Prakash
 *     
 *******************************************************************************/
package org.hudsonci.update.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.hudsonci.update.client.model.Plugin;
import org.hudsonci.update.client.model.UpdateSiteMetadata;

/**  
 * Updater that updates Hudson update-center either with newer version of
 * plugins from Jenkins or Maven Nexus
 * @author Winston Prakash
 */
public class UpdateCenterUpdater {

    public static final String JENKINS_UPDATE_CENTER_URL = "http://updates.jenkins-ci.org/update-center.json";
    private static final String HUDSON_WEB_FOLDER = "/home/hudson/public_html/";
    //private static final String HUDSON_WEB_FOLDER = "/Users/winstonp/Downloads/";
    private static final String PLUGINS_FOLDER = HUDSON_WEB_FOLDER + "downloads/plugins";
    private static final String UPDATABLE_JENKINS_PLUGINS_LIST = HUDSON_WEB_FOLDER + "UpdatableJenkinsPlugins.lst";
    private static final String IGNORE_PLUGINS_LIST = HUDSON_WEB_FOLDER + "IgnoreHudsonPlugins.lst";
    private static final String NEW_UPDATES_BASE_URL = "http://us3.maven.org:8085/rest";
    public static final String HUDSON_UPDATE_CENTER = HUDSON_WEB_FOLDER + "update-center.json";
    private static final String PLUGINS_DOWNLOAD_URL = "http://hudson-ci.org/downloads/plugins/";
    private static final String[] HUDSON_PLUGIN_PATHS = {"org/jvnet/hudson/plugins", "org/hudsonci/plugins"};
    private UpdateSiteMetadata hudsonUpdateSiteMetadata;

    public UpdateCenterUpdater() throws IOException {
        StringBuilder StringBuilder = new StringBuilder();
        BufferedReader in = new BufferedReader(new FileReader(HUDSON_UPDATE_CENTER));
        String str;
        while ((str = in.readLine()) != null) {
            StringBuilder.append(str);
            StringBuilder.append("\n");
        }
        in.close();
        String json = StringBuilder.toString();

        if (json.startsWith("updateCenter.post(")) {
            json = json.substring("updateCenter.post(".length());
        }

        if (json.endsWith(");")) {
            json = json.substring(0, json.lastIndexOf(");"));
        }
        hudsonUpdateSiteMetadata = UpdateCenterUtils.parse(json);
    }

    public void checkForNewUpdates() throws IOException {
        Set<String> ignorePlugins = new HashSet<String>();
        if (new File(IGNORE_PLUGINS_LIST).exists()) {
            ignorePlugins = readPluginList(IGNORE_PLUGINS_LIST);
        }
        for (String hudsonPluginPath : HUDSON_PLUGIN_PATHS) {
            try {
                System.out.println(new StringBuilder().append("Checking for updates at maven central (groupid path: ").append(hudsonPluginPath).append(")").toString());
                UpdateSiteMetadata hudsonNewUpdates = UpdateCenterUtils.getNewUpdates(NEW_UPDATES_BASE_URL,
                        hudsonPluginPath);
                int updatedPluginCount = 0;
                for (String pluginName : hudsonNewUpdates.getPlugins().keySet()) {
                    if (!ignorePlugins.contains(pluginName)) {
                        Plugin newPlugin = hudsonNewUpdates.findPlugin(pluginName);
                        Plugin hudsonPlugin = hudsonUpdateSiteMetadata.findPlugin(pluginName);
                        if ((hudsonPlugin == null) || newPlugin.isNewerThan(hudsonPlugin)) {
                            update(hudsonPlugin, newPlugin, false);
                            updatedPluginCount++;
                        }
                    }
                }
                if (updatedPluginCount == 0) {
                    System.out.println("\nNo new plugin found.\n");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void checkForJenkinsUpdates() throws IOException {
        if (!new File(UPDATABLE_JENKINS_PLUGINS_LIST).exists()) {
            return;
        }
        System.out.println("Checking for updates at Jenkins Update Center.");
        UpdateSiteMetadata jenkinsUpdateSiteMetadata = UpdateCenterUtils.parseFromUrl(JENKINS_UPDATE_CENTER_URL);

        Set<String> updatablePlugins = readPluginList(UPDATABLE_JENKINS_PLUGINS_LIST);
        int updatedPluginCount = 0;
        for (String pluginName : jenkinsUpdateSiteMetadata.getPlugins().keySet()) {
            if (updatablePlugins.contains(pluginName)) {
                Plugin jenkinsPlugin = jenkinsUpdateSiteMetadata.findPlugin(pluginName);
                Plugin hudsonPlugin = hudsonUpdateSiteMetadata.findPlugin(pluginName);

                if ((hudsonPlugin == null) || jenkinsPlugin.isNewerThan(hudsonPlugin)) {
                    update(hudsonPlugin, jenkinsPlugin, true);
                    updatedPluginCount++;
                }
            }
        }
        if (updatedPluginCount == 0) {
            System.out.println("\nNo new plugin found.\n");
        }
    }

    private Set<String> readPluginList(String filePath) throws IOException {
        Set<String> plugins = new HashSet<String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filePath));
            String updatablePlugin;
            while ((updatablePlugin = in.readLine()) != null) {
                plugins.add(updatablePlugin);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return plugins;
    }

    private void update(Plugin oldPlugin, Plugin newPlugin, boolean isJenkins) throws IOException {

        File pluginFolder = new File(PLUGINS_FOLDER, newPlugin.getName());
        File pluginVersionFolder = new File(pluginFolder, newPlugin.getVersion());
        pluginVersionFolder.mkdirs();
        UpdateCenterUtils.downloadFile(newPlugin.getUrl(), new File(pluginVersionFolder, newPlugin.getName() + ".hpi"));
        newPlugin.setUrl(
                PLUGINS_DOWNLOAD_URL + newPlugin.getName() + "/" + newPlugin.getVersion() + "/" + newPlugin.getName() + ".hpi");
        if (isJenkins) {
            newPlugin.setWiki(newPlugin.getWiki().replaceAll("jenkins", "hudson").replaceAll("JENKINS", "HUDSON"));
        }
        if (oldPlugin == null) {
            System.out.println(new StringBuilder().append("New Plugin found - ").append(newPlugin.getName()).append("(").append(newPlugin.getVersion()).append(")").toString());
            hudsonUpdateSiteMetadata.add(newPlugin);
        } else {
            System.out.println(new StringBuilder().append("Newer version available for \"").append(newPlugin.getName()).append("\" Current: ").append(oldPlugin.getVersion()).append(" New: ").append(newPlugin.getVersion()).toString());
            hudsonUpdateSiteMetadata.replacePlugin(oldPlugin, newPlugin);
        }
    }

    public void persistJson() throws IOException {
        String newJson = UpdateCenterUtils.getAsString(hudsonUpdateSiteMetadata);
        newJson = "updateCenter.post(" + newJson + ");";
        //System.out.println(newJson);
        File newUpdateCenter = new File(HUDSON_WEB_FOLDER + "update-center_new.json");
        BufferedWriter out = new BufferedWriter(new FileWriter(newUpdateCenter));
        out.write(newJson);
        out.close();
        File oldUpdateCenter = new File(HUDSON_UPDATE_CENTER);
        if (!oldUpdateCenter.delete()) {
            throw new IOException("Failed to delete " + HUDSON_UPDATE_CENTER);
        }

        if (!newUpdateCenter.renameTo(oldUpdateCenter)) {
            throw new IOException("Failed to rename " + newUpdateCenter.getName() + " to " + oldUpdateCenter.getName());
        }
    }

    public static void main(String[] args) throws IOException {

        UpdateCenterUpdater updateCenterUpdater = new UpdateCenterUpdater();
        updateCenterUpdater.checkForJenkinsUpdates();
        updateCenterUpdater.persistJson();
        updateCenterUpdater.checkForNewUpdates();
        updateCenterUpdater.persistJson();
    }
}
