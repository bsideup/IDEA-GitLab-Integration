package ru.trylogic.idea.gitlab.integration.actions;


import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitLabShowCommitInBrowserFromLogAction extends AbstractGitLabShowCommitInBrowserAction {

    @Nullable
    private static EventData collectData(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null || project.isDefault()) {
            return null;
        }

        GitCommit commit = e.getData(GitVcs.GIT_COMMIT);
        if (commit == null) {
            return null;
        }

        VirtualFile root = commit.getRoot();
        GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
        if (repository == null) {
            return null;
        }

        return new EventData(repository, commit);
    }

    @Override
    public void update(AnActionEvent e) {
        EventData eventData = collectData(e);
        e.getPresentation().setVisible(eventData != null);
        e.getPresentation().setEnabled(eventData != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        EventData eventData = collectData(e);
        if (eventData != null) {
            openInBrowser(eventData.getRepository(), eventData.getCommit().getHash().getValue());
        }
    }

    private static class EventData {
        @NotNull
        private final GitRepository myRepository;
        @NotNull
        private final GitCommit myCommit;

        private EventData(@NotNull GitRepository repository, @NotNull GitCommit commit) {
            myRepository = repository;
            myCommit = commit;
        }

        @NotNull
        public GitRepository getRepository() {
            return myRepository;
        }

        @NotNull
        public GitCommit getCommit() {
            return myCommit;
        }
    }

}