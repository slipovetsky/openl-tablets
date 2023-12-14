package org.openl.security.acl.workspace;

import static org.openl.security.acl.permission.AclPermission.CREATE;
import static org.openl.security.acl.permission.AclPermission.EDIT;
import static org.openl.security.acl.permission.AclPermission.VIEW;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.openl.rules.common.ProjectException;
import org.openl.rules.project.abstraction.ADeploymentProject;
import org.openl.rules.project.abstraction.AProject;
import org.openl.rules.project.abstraction.LockEngine;
import org.openl.rules.project.abstraction.RulesProject;
import org.openl.rules.workspace.WorkspaceUser;
import org.openl.rules.workspace.dtr.DesignTimeRepository;
import org.openl.rules.workspace.dtr.RepositoryException;
import org.openl.rules.workspace.lw.LocalWorkspace;
import org.openl.rules.workspace.uw.UserWorkspace;
import org.openl.rules.workspace.uw.UserWorkspaceListener;
import org.openl.security.acl.permission.AclPermission;
import org.openl.security.acl.repository.RepositoryAclService;

public class SecureUserWorkspaceImpl implements UserWorkspace {

    private final UserWorkspace userWorkspace;
    private final RepositoryAclService designRepositoryAclService;
    private final RepositoryAclService deployConfigRepositoryAclService;

    public SecureUserWorkspaceImpl(UserWorkspace userWorkspace,
            RepositoryAclService designRepositoryAclService,
            RepositoryAclService deployConfigRepositoryAclService) {
        this.userWorkspace = userWorkspace;
        this.designRepositoryAclService = designRepositoryAclService;
        this.deployConfigRepositoryAclService = deployConfigRepositoryAclService;
    }

    @Override
    public boolean hasProject(String repositoryId, String name) {
        try {
            RulesProject project = userWorkspace.getProject(repositoryId, name);
            return designRepositoryAclService.isGranted(project, List.of(VIEW));
        } catch (ProjectException e) {
            return false;
        }
    }

    @Override
    public List<RulesProject> getProjects(String repositoryId) {
        return userWorkspace.getProjects(repositoryId)
            .stream()
            .filter(e -> designRepositoryAclService.isGranted(e, List.of(VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public void activate() {
        userWorkspace.activate();
    }

    @Override
    public void addWorkspaceListener(UserWorkspaceListener listener) {
        userWorkspace.addWorkspaceListener(listener);
    }

    @Override
    public ADeploymentProject copyDDProject(ADeploymentProject project,
            String name,
            String comment) throws ProjectException {
        if (deployConfigRepositoryAclService.isGranted(project, List.of(VIEW))) {
            if (deployConfigRepositoryAclService
                .isGranted(project.getRepository().getId(), null, List.of(AclPermission.CREATE))) {
                return userWorkspace.copyDDProject(project, name, comment);
            }
        }
        throw new ProjectException("There is no permission for copying the deployment configuration.");
    }

    @Override
    public ADeploymentProject createDDProject(String name) throws RepositoryException {
        if (!userWorkspace.getDesignTimeRepository().hasDeployConfigRepo()) {
            throw new RepositoryException("There is no repository for deployment configurations.");
        }
        if (deployConfigRepositoryAclService.isGranted(
            userWorkspace.getDesignTimeRepository().getDeployConfigRepository().getId(),
            null,
            List.of(AclPermission.CREATE))) {
            return userWorkspace.createDDProject(name);
        }
        throw new RepositoryException("There is no permission for creating a new deployment configuration.");
    }

    @Override
    public ADeploymentProject getDDProject(String name) throws ProjectException {
        ADeploymentProject deploymentProject = userWorkspace.getDDProject(name);
        if (deployConfigRepositoryAclService.isGranted(deploymentProject, List.of(VIEW))) {
            return deploymentProject;
        }
        throw new ProjectException("There is no permission for reading the deployment configuration.");
    }

    @Override
    public ADeploymentProject getLatestDeploymentConfiguration(String name) {
        ADeploymentProject deploymentProject = userWorkspace.getLatestDeploymentConfiguration(name);
        if (deployConfigRepositoryAclService.isGranted(deploymentProject, List.of(VIEW))) {
            return deploymentProject;
        }
        return null;
    }

    @Override
    public List<ADeploymentProject> getDDProjects() throws ProjectException {
        return userWorkspace.getDDProjects()
            .stream()
            .filter(e -> deployConfigRepositoryAclService.isGranted(e, List.of(VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public DesignTimeRepository getDesignTimeRepository() {
        return userWorkspace.getDesignTimeRepository();
    }

    @Override
    public LocalWorkspace getLocalWorkspace() {
        return userWorkspace.getLocalWorkspace();
    }

    @Override
    public boolean hasDDProject(String name) {
        return userWorkspace.hasDDProject(name);
    }

    @Override
    public void passivate() {
        userWorkspace.passivate();
    }

    @Override
    public void refresh() {
        userWorkspace.refresh();
    }

    @Override
    public void syncProjects() {
        userWorkspace.syncProjects();
    }

    @Override
    public String getActualName(AProject project) throws ProjectException, IOException {
        if (designRepositoryAclService.isGranted(project, List.of(VIEW))) {
            return userWorkspace.getActualName(project);
        } else {
            throw new ProjectException("There is no permission for reading the project.");
        }
    }

    @Override
    public void release() {
        userWorkspace.release();
    }

    @Override
    public void removeWorkspaceListener(UserWorkspaceListener listener) {
        userWorkspace.removeWorkspaceListener(listener);
    }

    @Override
    public RulesProject uploadLocalProject(String repositoryId,
            String name,
            String projectFolder,
            String comment) throws ProjectException {
        if (userWorkspace.hasProject(repositoryId, name)) {
            String path = userWorkspace.getDesignTimeRepository().getRulesLocation() + name;
            if (designRepositoryAclService.isGranted(repositoryId, path, List.of(EDIT))) {
                return userWorkspace.uploadLocalProject(repositoryId, name, projectFolder, comment);
            } else {
                throw new ProjectException(String.format("There is no permission for modifying '%s'.", path));
            }
        } else {
            if (designRepositoryAclService.isGranted(repositoryId, null, List.of(CREATE))) {
                return userWorkspace.uploadLocalProject(repositoryId, name, projectFolder, comment);
            } else {
                throw new ProjectException("There is no permission for creating a new project.");
            }
        }
    }

    @Override
    public Optional<RulesProject> getProjectByPath(String repositoryId, String realPath) {
        Optional<RulesProject> rulesProjectOptional = userWorkspace.getProjectByPath(repositoryId, realPath);
        if (rulesProjectOptional
            .isPresent() && !designRepositoryAclService.isGranted(rulesProjectOptional.get(), List.of(VIEW))) {
            return Optional.empty();
        }
        return rulesProjectOptional;
    }

    @Override
    public WorkspaceUser getUser() {
        return userWorkspace.getUser();
    }

    @Override
    public RulesProject getProject(String repositoryId, String name) throws ProjectException {
        RulesProject rulesProject = userWorkspace.getProject(repositoryId, name);
        if (rulesProject != null && !designRepositoryAclService.isGranted(rulesProject, List.of(VIEW))) {
            throw new ProjectException("There is no permission for reading the project.");
        }
        return rulesProject;
    }

    @Override
    public RulesProject getProject(String repositoryId, String name, boolean refreshBefore) throws ProjectException {
        RulesProject rulesProject = userWorkspace.getProject(repositoryId, name, refreshBefore);
        if (rulesProject != null && !designRepositoryAclService.isGranted(rulesProject, List.of(VIEW))) {
            throw new ProjectException("There is no permission for reading the project.");
        }
        return rulesProject;
    }

    @Override
    public Collection<RulesProject> getProjects() {
        return userWorkspace.getProjects()
            .stream()
            .filter(e -> designRepositoryAclService.isGranted(e, List.of(VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public Collection<RulesProject> getProjects(boolean refreshBefore) {
        return userWorkspace.getProjects(refreshBefore)
            .stream()
            .filter(e -> designRepositoryAclService.isGranted(e, List.of(VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public LockEngine getProjectsLockEngine() {
        return userWorkspace.getProjectsLockEngine();
    }

    @Override
    public boolean isOpenedOtherProject(AProject project) {
        return userWorkspace.isOpenedOtherProject(project);
    }
}