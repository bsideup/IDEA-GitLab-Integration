package ru.trylogic.idea.gitlab.integration.utils;

import com.intellij.openapi.util.text.StringUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GitlabUrlUtil {

    @Nullable
    public static String findRemoteUrl(@NotNull GitRepository repository) {
        for (GitRemote remote : repository.getRemotes()) {
            if (remote.getName().equals("origin")) {
                return remote.getFirstUrl();
            }
        }
        return null;
    }

    public static String makeRepoUrlFromRemoteUrl(@NotNull String remoteUrl) {
        String cleanedFromDotGit = StringUtil.trimEnd(remoteUrl, ".git");

        if (remoteUrl.startsWith("http://")) {
            return cleanedFromDotGit;
        } else if (remoteUrl.startsWith("git@")) {
            String cleanedFromGitAt = StringUtil.trimStart(cleanedFromDotGit, "git@");

            return "http://" + StringUtil.replace(cleanedFromGitAt, ":", "/");
        } else {
            throw new IllegalStateException("Invalid remote Gitlab url: " + remoteUrl);
        }
    }
}
