package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.GitlabIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GitLabOpenInBrowserBaseAction extends DumbAwareAction {

    public static final Logger LOG = Logger.getInstance("gitlab");

    public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

    protected GitLabOpenInBrowserBaseAction(@Nullable String text, @Nullable String description) {
        super(text, description, GitlabIcons.Gitlab_icon);
    }

    static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    static void showError(Project project, String title, String message) {
        showError(project, title, message, null);
    }

    static void showError(Project project, String title, String message, String debugInfo) {
        LOG.warn(title + "; " + message);
        if (debugInfo != null) {
            LOG.warn(debugInfo);
        }
        VcsNotifier.getInstance(project).notifyError(title, message);
    }

    @Nullable
    public static String getBranchNameOnRemote(@NotNull Project project, @NotNull GitRepository repository) {
        GitLocalBranch currentBranch = repository.getCurrentBranch();
        if (currentBranch == null) {
            showError(project, CANNOT_OPEN_IN_BROWSER,
                    "Can't open the file on GitLab when repository is on detached HEAD. Please checkout a branch.");
            return null;
        }

        GitRemoteBranch tracked = currentBranch.findTrackedBranch(repository);
        if (tracked == null) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't open the file on GitLab when current branch doesn't have a tracked branch.",
                    "Current branch: " + currentBranch + ", tracked info: " + repository.getBranchTrackInfos());
            return null;
        }

        return tracked.getNameForRemoteOperations();
    }

    @Override
    public void update(final AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (project == null || project.isDefault() || virtualFile == null) {
            setVisibleEnabled(e, false, false);
            return;
        }

        final GitRepository gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile);
        if (gitRepository == null) {
            setVisibleEnabled(e, false, false);
            return;
        }

        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        if (changeListManager.isUnversioned(virtualFile)) {
            setVisibleEnabled(e, true, false);
            return;
        }

        Change change = changeListManager.getChange(virtualFile);
        if (change != null && change.getType() == Change.Type.NEW) {
            setVisibleEnabled(e, true, false);
            return;
        }

        setVisibleEnabled(e, true, true);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (virtualFile == null || project == null || project.isDisposed()) {
            return;
        }

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        final GitRepository repository = manager.getRepositoryForFile(virtualFile);
        if (repository == null) {
            StringBuilder details = new StringBuilder("file: " + virtualFile.getPresentableUrl() + "; Git repositories: ");
            for (GitRepository repo : manager.getRepositories()) {
                details.append(repo.getPresentableUrl()).append("; ");
            }
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find git repository", details.toString());
            return;
        }

        final String rootPath = repository.getRoot().getPath();
        final String path = virtualFile.getPath();

        List<AnAction> remoteSelectedActions = new ArrayList<AnAction>();

        for (GitRemote remote : repository.getRemotes()) {
            remoteSelectedActions.add(remoteSelectedAction(project, repository, virtualFile, editor, remote, rootPath, path));
        }

        if (remoteSelectedActions.size() > 1) {
            DefaultActionGroup remotesActionGroup = new DefaultActionGroup();
            remotesActionGroup.addAll(remoteSelectedActions);
            DataContext dataContext = e.getDataContext();
            final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                    "Select remote",
                    remotesActionGroup,
                    dataContext,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    true);

            popup.showInBestPositionFor(dataContext);
        } else if (remoteSelectedActions.size() == 1) {
            remoteSelectedActions.get(0).actionPerformed(null);
        } else {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find gitlab remote");
        }
    }

    protected AnAction remoteSelectedAction(Project project, GitRepository repository, VirtualFile virtualFile, Editor editor, GitRemote remote, String rootPath, String path) {
        return null;
    }
}

class RemoteSelectedBaseAction extends AnAction {

    protected final Editor editor;

    protected final GitRemote remote;

    protected final String rootPath;

    protected final String path;

    protected final Project project;

    protected final GitRepository repository;

    public RemoteSelectedBaseAction(@NotNull Project project, @NotNull GitRepository repository, @Nullable Editor editor,
                                    @NotNull GitRemote remote, @NotNull String rootPath, @NotNull String path) {
        super(remote.getName());
        this.project = project;
        this.repository = repository;
        this.editor = editor;
        this.remote = remote;
        this.rootPath = rootPath;
        this.path = path;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        setEnabledInModalContext(true);
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@Nullable AnActionEvent e) {
        if (!path.startsWith(rootPath)) {
            GitLabOpenInBrowserBaseAction.showError(project, GitLabOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER,
                    "File is not under repository root", "Root: " + rootPath + ", file: " + path);
            return;
        }

        String branch = GitLabOpenInBrowserBaseAction.getBranchNameOnRemote(project, repository);
        if (branch == null) {
            return;
        }

        String remoteUrl = remote.getFirstUrl();

        if (remoteUrl == null) {
            GitLabOpenInBrowserBaseAction.showError(project, GitLabOpenInBrowserBaseAction.CANNOT_OPEN_IN_BROWSER,
                    "Can't obtain url for remote", remote.getName());
            return;
        }

        String relativePath = path.substring(rootPath.length());
        String urlToOpen = makeUrlToOpen(relativePath, branch, remoteUrl);
        if (urlToOpen == null) {
            GitLabOpenInBrowserBaseAction.showError(project, GitLabOpenInBrowserBaseAction.CANNOT_OPEN_IN_BROWSER,
                    "Can't create properly url", remote.getFirstUrl());
            return;
        }

        BrowserUtil.launchBrowser(urlToOpen);
    }

    protected String makeUrlToOpen(String relativePath, String branch, String remoteUrl) {
        return null;
    }
}