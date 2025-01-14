package org.openl.rules.workspace.lw.impl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openl.rules.repository.api.UserInfo;
import org.openl.rules.workspace.WorkspaceUserImpl;
import org.openl.rules.workspace.lw.LocalWorkspace;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class LocalWorkspaceManagerImplTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private LocalWorkspaceManagerImpl manager;

    @Before
    public void init() throws Exception {
        manager = new LocalWorkspaceManagerImpl();
        manager.setWorkspaceHome(tempFolder.getRoot().getAbsolutePath());
        manager.init();
    }

    @Test
    public void removeWorkspaceOnSessionTimeout() {
        WorkspaceUserImpl user = new WorkspaceUserImpl("user.1",
            (username) -> new UserInfo("user.1", "user.1@email", "User 1"));
        LocalWorkspace workspace1 = manager.getWorkspace(user.getUserId());
        String repoId = "design";

        // Must return cached version
        LocalWorkspace workspace2 = manager.getWorkspace(user.getUserId());
        assertSame(workspace1, workspace2);

        // Session timeout
        workspace1.release();

        // Must create new instance
        workspace2 = manager.getWorkspace(user.getUserId());
        assertNotSame(workspace1, workspace2);
        assertNotSame(workspace1.getRepository(repoId), workspace2.getRepository(repoId));
    }

    @Test
    public void dontCreateEmptyFolder() {
        LocalWorkspace workspace1 = manager.getWorkspace(
            new WorkspaceUserImpl("user.1", (username) -> new UserInfo("user.1", "user.1@email", "User 1"))
                .getUserId());
        assertFalse(workspace1.getLocation().exists());
    }
}