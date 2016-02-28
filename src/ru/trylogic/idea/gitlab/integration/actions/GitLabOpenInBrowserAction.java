package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.trylogic.idea.gitlab.integration.utils.GitlabUrlUtil;

public class GitLabOpenInBrowserAction extends GitLabOpenInBrowserBaseAction {

    protected GitLabOpenInBrowserAction() {
        super("Open on GitLab", "Open corresponding link in browser");
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

    @Override
    protected AnAction remoteSelectedAction(Project project, GitRepository repository, VirtualFile virtualFile, Editor editor, GitRemote remote, String rootPath, String path) {
        return new RemoteSelectedAction(project, repository, editor, remote, rootPath, path);
    }
}

class RemoteSelectedAction extends RemoteSelectedBaseAction {

    public RemoteSelectedAction(@NotNull Project project, @NotNull GitRepository repository, @Nullable Editor editor,
                                @NotNull GitRemote remote, @NotNull String rootPath, @NotNull String path) {
        super(project, repository, editor, remote, rootPath, path);
    }

    @Override
    protected String makeUrlToOpen(String relativePath, String branch, String remoteUrl) {
        return GitLabOpenInBrowserAction.makeUrlToOpen(editor, relativePath, branch, remoteUrl);
    }
}