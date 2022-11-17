package org.openl.rules.webstudio.security;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openl.rules.common.CommonVersion;
import org.openl.rules.common.ProjectException;
import org.openl.rules.project.abstraction.ADeploymentProject;
import org.openl.rules.project.abstraction.AProject;
import org.openl.rules.repository.api.BranchRepository;
import org.openl.rules.repository.api.FolderRepository;
import org.openl.rules.repository.api.Repository;
import org.openl.rules.workspace.dtr.DesignTimeRepository;
import org.openl.rules.workspace.dtr.DesignTimeRepositoryListener;
import org.openl.rules.workspace.dtr.RepositoryException;
import org.openl.rules.workspace.dtr.impl.MappedRepository;
import org.openl.security.acl.permission.AclPermission;
import org.openl.security.acl.repository.*;

public class SecureDesignTimeRepositoryImpl implements DesignTimeRepository {

    private DesignTimeRepository designTimeRepository;

    private DesignRepositoryAclService designRepositoryAclService;

    public DesignRepositoryAclService getDesignRepositoryAclService() {
        return designRepositoryAclService;
    }

    public void setDesignRepositoryAclService(DesignRepositoryAclService designRepositoryAclService) {
        this.designRepositoryAclService = designRepositoryAclService;
    }

    public DesignTimeRepository getDesignTimeRepository() {
        return designTimeRepository;
    }

    public void setDesignTimeRepository(DesignTimeRepository designTimeRepository) {
        this.designTimeRepository = designTimeRepository;
    }

    @Override
    public List<Repository> getRepositories() {
        return designTimeRepository.getRepositories().stream().map(this::wrapToSecureRepo).collect(Collectors.toList());
    }

    @Override
    public Repository getRepository(String id) {
        return wrapToSecureRepo(designTimeRepository.getRepository(id));
    }

    protected Repository wrapToSecureRepo(Repository repository) {
        if (repository == null) {
            return null;
        }
        if (repository instanceof MappedRepository) {
            return new SecureDesignMappedRepository((MappedRepository) repository, getDesignRepositoryAclService());
        } else if (repository instanceof BranchRepository) {
            return new SecureDesignBranchRepository((BranchRepository) repository, getDesignRepositoryAclService());
        } else if (repository instanceof FolderRepository) {
            return new SecureDesignFolderRepository((FolderRepository) repository, getDesignRepositoryAclService());
        } else {
            return new SecureDesignRepository(repository, getDesignRepositoryAclService());
        }
    }

    @Override
    public AProject getProject(String repositoryId, String name) throws ProjectException {
        AProject project = designTimeRepository.getProject(repositoryId, name);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.VIEW))) {
            return project;
        }
        throw new ProjectException("Access denied");
    }

    @Override
    public AProject getProject(String repositoryId, String name, CommonVersion version) {
        AProject project = designTimeRepository.getProject(repositoryId, name, version);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.VIEW))) {
            return project;
        }
        return null;
    }

    @Override
    public AProject getProjectByPath(String repositoryId,
            String branch,
            String path,
            String version) throws IOException {
        AProject project = designTimeRepository.getProjectByPath(repositoryId, branch, path, version);
        if (designRepositoryAclService.isGranted(project, List.of(AclPermission.VIEW))) {
            return project;
        }
        throw new AccessDeniedException("Access denied");
    }

    @Override
    public Collection<AProject> getProjects() {
        return designTimeRepository.getProjects()
            .stream()
            .filter(e -> designRepositoryAclService.isGranted(e, List.of(AclPermission.VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public List<? extends AProject> getProjects(String repositoryId) {
        return designTimeRepository.getProjects(repositoryId)
            .stream()
            .filter(e -> designRepositoryAclService.isGranted(e, List.of(AclPermission.VIEW)))
            .collect(Collectors.toList());
    }

    @Override
    public boolean hasProject(String repositoryId, String name) {
        return designTimeRepository.hasProject(repositoryId, name);
    }

    @Override
    public ADeploymentProject.Builder createDeploymentConfigurationBuilder(String name) {
        return designTimeRepository.createDeploymentConfigurationBuilder(name);
    }

    @Override
    public List<ADeploymentProject> getDDProjects() throws RepositoryException {
        return designTimeRepository.getDDProjects();
    }

    @Override
    public void refresh() {
        designTimeRepository.refresh();
    }

    @Override
    public boolean hasDDProject(String name) {
        return designTimeRepository.hasDDProject(name);
    }

    @Override
    public void addListener(DesignTimeRepositoryListener listener) {
        designTimeRepository.addListener(listener);
    }

    @Override
    public void removeListener(DesignTimeRepositoryListener listener) {
        designTimeRepository.removeListener(listener);
    }

    @Override
    public String getRulesLocation() {
        return designTimeRepository.getRulesLocation();
    }

    @Override
    public List<String> getExceptions() {
        return designTimeRepository.getExceptions();
    }

    @Override
    public boolean hasDeployConfigRepo() {
        return designTimeRepository.hasDeployConfigRepo();
    }

    @Override
    public Repository getDeployConfigRepository() {
        return designTimeRepository.getDeployConfigRepository();
    }

    @Override
    public String getDeployConfigLocation() {
        return designTimeRepository.getDeployConfigLocation();
    }
}