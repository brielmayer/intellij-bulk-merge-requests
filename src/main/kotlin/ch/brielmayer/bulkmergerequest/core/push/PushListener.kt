package ch.brielmayer.bulkmergerequest.core.push

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository

/**
 * Hears about every repository the IDE has just pushed and hands it to [PushNotifier].
 *
 * [GitPushListener] is experimental API, which the Marketplace accepts. The earlier route, a link
 * inside the IDE's own "Push successful" notification, needs the internal `GitPushNotificationCustomizer`
 * and was refused by the Marketplace.
 */
class PushListener(private val project: Project) : GitPushListener {

    override fun onCompleted(repository: GitRepository, pushResult: GitPushRepoResult) {
        project.service<PushNotifier>().pushed(repository, pushResult)
    }
}
