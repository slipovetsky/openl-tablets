package org.openl.itest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openl.itest.core.JettyServer;
import org.openl.rules.ruleservice.deployer.RulesDeployerService;

import io.minio.MakeBucketArgs;
import io.minio.StatObjectArgs;
import okio.Path;

public class RunMinioDeployIfAbsentTest extends AbstractMinioTest {

    @Test
    public void testWhenDeployJarsIfAbsent() throws Exception {
        JettyServer server = null;
        try {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            try (var deployer = new RulesDeployerService(config::get)) {
                deployer.deploy(Path.get("test-resources/openl/multiple-deployment-datasource.zip").toFile(), false);
            }
            verifyS3Repository();
            final var beforeStartProject1 = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object("deploy/multiple-deployment-datasource/project1")
                .build());
            final var beforeStartProject2 = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object("deploy/multiple-deployment-datasource/project2")
                .build());

            config.put("ruleservice.datasource.deploy.classpath.jars", "IF_ABSENT");
            server = JettyServer.start(config);
            var client = server.client();
            client.test("test-resources-smoke/stage1");

            // verify that projects are not redeployed
            var actualProject1 = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object("deploy/multiple-deployment-datasource/project1")
                .build());
            assertTrue(beforeStartProject1.lastModified().isEqual(actualProject1.lastModified()));
            assertEquals(beforeStartProject1.versionId(), actualProject1.versionId());

            var actualProject2 = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object("deploy/multiple-deployment-datasource/project2")
                .build());
            assertTrue(beforeStartProject2.lastModified().isEqual(actualProject2.lastModified()));
            assertEquals(beforeStartProject2.versionId(), actualProject2.versionId());
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

}
