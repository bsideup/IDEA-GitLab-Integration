package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.trylogic.idea.gitlab.integration.utils.GitlabUrlUtil;

import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitLabOpenInBrowserMergeRequestAction extends GitLabOpenInBrowserBaseAction {

    protected GitLabOpenInBrowserMergeRequestAction() {
        super("Open on GitLab(Merge Request)", "Open corresponding Merge Request link in browser");
    }

    static String getMergeRequestId(Project project, GitRepository repository, String commitId) {
        String mergeCommitId = getMergeCommitId(project, repository, commitId);
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.LOG);
        handler.addParameters("-l");
        handler.addParameters("--format=%B");
        handler.addParameters(mergeCommitId);
        Git git = ServiceManager.getService(project, Git.class);
        GitCommandResult result = git.runCommand(handler);
        Pattern p = Pattern.compile("See merge request !([0-9]*)");
        for (String line: result.getOutput()) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }

        showError(project, CANNOT_OPEN_IN_BROWSER, "Can't open the Merge Request page on GitLab when commit id doesn't found at git-log.",
                  "Commit id: " + commitId);
        return null;
    }

    static String getMergeCommitId(Project project, GitRepository repository, String commitId) {
        String branch = getBranchNameOnRemote(project, repository);
        GitLineHandler ancestryPathHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.REV_LIST);
        ancestryPathHandler.addParameters("--ancestry-path");
        ancestryPathHandler.addParameters(commitId + ".." + branch);
        Git git = ServiceManager.getService(project, Git.class);
        List<String> ancestryPathResult = git.runCommand(ancestryPathHandler).getOutput();

        GitLineHandler firstParentHandler = new GitLineHandler(project, repository.getRoot(), GitCommand.REV_LIST);
        firstParentHandler.addParameters("--first-parent");
        firstParentHandler.addParameters(commitId + ".." + branch);
        List<String>  firstParentResult = git.runCommand(firstParentHandler).getOutput();

        ancestryPathResult.retainAll(new HashSet<String>(firstParentResult));
        return ancestryPathResult.get(ancestryPathResult.size() - 1);
    }

    @Nullable
    static String getCurrentLineRevisionNumber(Project project, VirtualFile virtualFile, Editor editor) {
        GitAnnotationProvider annotationProvider = new GitAnnotationProvider(project);
        FileAnnotation fileAnnotation;
        try {
            fileAnnotation = annotationProvider.annotate(virtualFile);
        } catch (VcsException e) {
            showError(project, CANNOT_OPEN_IN_BROWSER, "Can't open the Merge Request page on GitLab due to the exception: " + e);
            return null;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        final int lineNumber = editor.getDocument().getLineNumber(selectionModel.getSelectionStart());
        final GitRevisionNumber gitRev = new GitRevisionNumber(fileAnnotation.getLineRevisionNumber(lineNumber).toString());
        return gitRev.getRev();
    }

    @Nullable
    static String makeUrlToOpen(@NotNull Project project,
                                @NotNull String remoteUrl,
                                @NotNull VirtualFile virtualFile,
                                @Nullable Editor editor,
                                @NotNull GitRepository repository) {
        final String commitId = getCurrentLineRevisionNumber(project, virtualFile, editor);
        final String mergeRequestId = getMergeRequestId(project, repository, commitId);
        if (mergeRequestId == null) {
            return null;
        }
        final StringBuilder builder = new StringBuilder();
        final String repoUrl = GitlabUrlUtil.makeRepoUrlFromRemoteUrl(remoteUrl);
        if (repoUrl == null) {
            return null;
        }
        builder.append(repoUrl).append("/merge_requests/").append(mergeRequestId);
        return builder.toString();
    }

    @Override
    protected AnAction remoteSelectedAction(Project project, GitRepository repository, VirtualFile virtualFile, Editor editor, GitRemote remote, String rootPath, String path) {
        return new RemoteSelectedMergeRequestAction(project, repository, editor, remote, rootPath, path, virtualFile);
    }
}

class RemoteSelectedMergeRequestAction extends RemoteSelectedBaseAction {

    private final VirtualFile virtualFile;

    public RemoteSelectedMergeRequestAction(@NotNull Project project, @NotNull GitRepository repository, @Nullable Editor editor,
                                            @NotNull GitRemote remote, @NotNull String rootPath, @NotNull String path,
                                            @NotNull VirtualFile virtualFile) {
        super(project, repository, editor, remote, rootPath, path);
        this.virtualFile = virtualFile;
    }

    @Override
    protected String makeUrlToOpen(String relativePath, String branch, String remoteUrl) {
        return GitLabOpenInBrowserMergeRequestAction.makeUrlToOpen(project, remoteUrl, virtualFile, editor, repository);
    }
}
