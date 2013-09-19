package ru.trylogic.idea.gitlab.integration.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;
import icons.GitlabIcons;
import ru.trylogic.idea.gitlab.integration.utils.GitlabUrlUtil;

abstract class AbstractGitLabShowCommitInBrowserAction extends DumbAwareAction {

    public AbstractGitLabShowCommitInBrowserAction() {
        super("Open on GitLab", "Open the selected commit in browser", GitlabIcons.Gitlab_icon);
    }

    protected static void openInBrowser(GitRepository repository, String revisionHash) {

        String remote = GitlabUrlUtil.findRemoteUrl(repository);
        final String repoUrl = GitlabUrlUtil.makeRepoUrlFromRemoteUrl(remote);

        String url = repoUrl + "/commit/" + revisionHash;
        BrowserUtil.launchBrowser(url);
    }

}