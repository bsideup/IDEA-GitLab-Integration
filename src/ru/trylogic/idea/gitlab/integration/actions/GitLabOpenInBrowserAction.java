package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
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
import ru.trylogic.idea.gitlab.integration.utils.GitlabUrlUtil;

public class GitLabOpenInBrowserAction extends DumbAwareAction {

    public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

    protected GitLabOpenInBrowserAction() {
        super("Open on GitLab", "Open corresponding link in browser", GitlabIcons.Gitlab_icon);
    }

    static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    static void showError(Project project, String cannotOpenInBrowser) {
        showError(project, cannotOpenInBrowser, null);
    }

    static void showError(Project project, String cannotOpenInBrowser, String s) {
        showError(project, cannotOpenInBrowser, s, null);
    }

    static void showError(Project project, String cannotOpenInBrowser, String s, String s1) {
        System.out.println(cannotOpenInBrowser + ";" + s + ";" + s1);
    }

    @Nullable
    static String makeUrlToOpen(@Nullable Editor editor,
                                @NotNull String relativePath,
                                @NotNull String branch,
                                @NotNull String remoteUrl) {
        final StringBuilder builder = new StringBuilder();
        final String repoUrl = GitlabUrlUtil.makeRepoUrlFromRemoteUrl(remoteUrl);
        if (repoUrl == null) {
            return null;
        }
        builder.append(repoUrl).append("/blob/").append(branch).append(relativePath);

        if (editor != null && editor.getDocument().getLineCount() >= 1) {
            // lines are counted internally from 0, but from 1 on gitlab
            SelectionModel selectionModel = editor.getSelectionModel();
            final int begin = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
            final int selectionEnd = selectionModel.getSelectionEnd();
            int end = editor.getDocument().getLineNumber(selectionEnd) + 1;
            if (editor.getDocument().getLineStartOffset(end - 1) == selectionEnd) {
                end -= 1;
            }
            builder.append("#L").append(begin).append('-').append(end);
        }

        return builder.toString();
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

        if (repository.getRemotes().size() == 0) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find gitlab remote");
            return;
        }

        final String rootPath = repository.getRoot().getPath();
        final String path = virtualFile.getPath();
        DefaultActionGroup remotesActionGroup = new DefaultActionGroup();
        remotesActionGroup.add(new RemoteSelectedAction(project, repository, editor, repository.getRemotes().iterator().next(), rootPath, path));


        DataContext dataContext = e.getDataContext();
        final ListPopup popup =
                JBPopupFactory.getInstance().createActionGroupPopup(
                        "Select remote",
                        remotesActionGroup,
                        dataContext,
                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                        true);

        popup.showInBestPositionFor(dataContext);
    }


}

class RemoteSelectedAction extends AnAction {

    private final Editor editor;

    private final GitRemote remote;

    private final String rootPath;

    private final String path;

    private final Project project;

    private final GitRepository repository;

    public RemoteSelectedAction(@NotNull Project project, @NotNull GitRepository repository, @Nullable Editor editor,
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
    public void actionPerformed(AnActionEvent anActionEvent) {
        if (!path.startsWith(rootPath)) {
            GitLabOpenInBrowserAction.showError(project, GitLabOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER,
                    "File is not under repository root", "Root: " + rootPath + ", file: " + path);
            return;
        }

        String branch = GitLabOpenInBrowserAction.getBranchNameOnRemote(project, repository);
        if (branch == null) {
            return;
        }
        
        String remoteUrl = remote.getFirstUrl();

        if (remoteUrl == null) {
            GitLabOpenInBrowserAction.showError(project, GitLabOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER,
                    "Can't obtain url for remote", remote.getName());
            return;
        }

        String relativePath = path.substring(rootPath.length());
        String urlToOpen = GitLabOpenInBrowserAction.makeUrlToOpen(editor, relativePath, branch, remoteUrl);
        if (urlToOpen == null) {
            GitLabOpenInBrowserAction.showError(project, GitLabOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER,
                    "Can't create properly url", remote.getFirstUrl());
            return;
        }

        BrowserUtil.launchBrowser(urlToOpen);
    }
}