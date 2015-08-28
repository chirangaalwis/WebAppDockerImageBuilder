/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.docker.images;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.docker.images.exceptions.DockerImageBuilderException;
import org.wso2.carbon.docker.images.interfaces.IDockerWebAppImageBuilder;
import org.wso2.carbon.docker.images.utility.FileOutputThread;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DockerWebAppImageBuilder implements IDockerWebAppImageBuilder {

    private final DockerClient dockerClient;

    private static final Log LOG = LogFactory.getLog(DockerWebAppImageBuilder.class);

    public DockerWebAppImageBuilder() throws DockerImageBuilderException {
        try {
            dockerClient = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            String message = String.format("Could not create the DockerClient");
            LOG.error(message, e);
            throw new DockerImageBuilderException(message, e);
        }
    }

    public void buildImage(String creator, String imageName, String imageVersion, Path artifactPath) throws
            DockerImageBuilderException {
        String dockerImageName = generateImageIdentifier(creator, imageName, imageVersion);
        try {
            setupEnvironment(artifactPath);
            dockerClient.build(artifactPath.getParent(), dockerImageName);
        } catch (Exception e) {
            String message = String.format("Could not create the docker image[image-identifier]: "
                    + "%s", dockerImageName);
            LOG.error(message, e);
            throw new DockerImageBuilderException(message, e);
        }
    }

    public void removeImages(String creator, String imageName, String imageVersion) throws
            DockerImageBuilderException {
        String dockerImageName = generateImageIdentifier(creator, imageName, imageVersion);
        try {
            dockerClient.removeImage(dockerImageName);
        } catch (Exception e) {
            String message = String.format("Could not remove the docker image[image-identifier]: "
                    + "%s", dockerImageName);
            LOG.error(message, e);
            throw new DockerImageBuilderException(message, e);
        }
    }

    private void setupEnvironment(Path filePath) throws IOException {
        Path parentDirectory = filePath.getParent();
        File dockerFile;

        if(parentDirectory != null) {
            String parentDirectoryPath = parentDirectory.toString();
            dockerFile = new File(parentDirectoryPath + File.separator + "Dockerfile");
        }
        else {
            // TODO: to be tested
            dockerFile = new File("Dockerfile");
        }

        boolean exists = dockerFile.exists();
        if(!exists) {
            boolean created = dockerFile.createNewFile();
            if(created) {
                LOG.debug("New Dockerfile created.");
            }
        }

        // get base Apache Tomcat Dockerfile content from the application's file
        List<String> baseDockerFileContent = getDockerFileContent();

        /*
        set up a new Dockerfile with the specified WAR file deploying command in the Apache
        Tomcat server
        */
        baseDockerFileContent.add(2, "ADD " + filePath.getFileName().toString()
                + " /usr/local/tomcat/webapps/");
        setWebAppDockerFile(dockerFile, baseDockerFileContent);
    }

    private void setWebAppDockerFile(File dockerFilePath, List<String> data) {
        FileOutputThread outputThread = new FileOutputThread(dockerFilePath.getAbsolutePath()
                , data);
        outputThread.run();
    }

    private List<String> getDockerFileContent() {
        List<String> baseContent = new ArrayList<String>();

        baseContent.add("FROM tomcat");
        baseContent.add("MAINTAINER user");
        baseContent.add("CMD [\"catalina.sh\", \"run\"]");

        return baseContent;
    }

    private String generateImageIdentifier(String creator, String imageName, String imageVersion) {
        String imageIdentifier;
        if(imageVersion == null) {
            imageIdentifier = creator + "/" + imageName + ":latest";
        }
        else {
            imageIdentifier = creator + "/" + imageName + ":" + imageVersion;
        }
        return imageIdentifier;
    }

}
