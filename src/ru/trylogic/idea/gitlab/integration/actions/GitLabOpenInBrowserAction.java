package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import ru.trylogic.idea.gitlab.integration.utils.GitlabUrlUtil;
import icons.GitlabIcons;

public class GitLabOpenInBrowserAction extends DumbAwareAction {

    public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

    static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    protected GitLabOpenInBrowserAction() {
        super("Open on GitLab", "Open corresponding link in browser", GitlabIcons.Gitlab_icon);
    }

    @Override
    public void update(final AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (project == null || project.isDefault() || virtualFile == null) {
            setVisibleEnabled(e, false, false);
            return;
        }
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

        final GitRepository gitRepository = manager.getRepositoryForFile(virtualFile);
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

        String urlToOpen = getUrl(project, virtualFile, editor);
        if (urlToOpen != null) {
            BrowserUtil.launchBrowser(urlToOpen);
        }
    }

    @Nullable
    public static String getUrl(@NotNull Project project, @NotNull VirtualFile virtualFile, @Nullable Editor editor) {

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        final GitRepository repository = manager.getRepositoryForFile(virtualFile);
        if (repository == null) {
            StringBuilder details = new StringBuilder("file: " + virtualFile.getPresentableUrl() + "; Git repositories: ");
            for (GitRepository repo : manager.getRepositories()) {
                details.append(repo.getPresentableUrl()).append("; ");
            }
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find git repository", details.toString());
            return null;
        }

        final String remoteUrl = GitlabUrlUtil.findRemoteUrl(repository);
        if (remoteUrl == null) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't find gitlab remote");
            return null;
        }

        final String rootPath = repository.getRoot().getPath();
        final String path = virtualFile.getPath();
        if (!path.startsWith(rootPath)) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "File is not under repository root", "Root: " + rootPath + ", file: " + path);
            return null;
        }

        String branch = getBranchNameOnRemote(project, repository);
        if (branch == null) {
            return null;
        }

        String relativePath = path.substring(rootPath.length());
        String urlToOpen = makeUrlToOpen(editor, relativePath, branch, remoteUrl);
        if (urlToOpen == null) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't create properly url", remoteUrl);
            return null;
        }

        return urlToOpen;
    }

    private static void showError(Project project, String cannotOpenInBrowser) {
        showError(project, cannotOpenInBrowser, null);
    }

    private static void showError(Project project, String cannotOpenInBrowser, String s) {
        showError(project, cannotOpenInBrowser, s, null);
    }

    private static void showError(Project project, String cannotOpenInBrowser, String s, String s1) {
        System.out.println(cannotOpenInBrowser + ";" + s + ";" + s1);
    }

    @Nullable
    private static String makeUrlToOpen(@Nullable Editor editor,
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


}
