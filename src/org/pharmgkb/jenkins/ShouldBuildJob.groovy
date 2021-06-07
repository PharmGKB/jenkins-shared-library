package org.pharmgkb.jenkins

import hudson.model.Queue
import hudson.model.Result
import jenkins.model.Jenkins

import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * Checks if build should proceed.
 *
 * @author Mark Woon
 */
class ShouldBuildJob implements Serializable {
  // https://stackoverflow.com/questions/51555910/how-to-know-inside-jenkinsfile-script-that-current-build-is-an-replay/52302879#52302879
  private static final String sf_replayClassName = "org.jenkinsci.plugins.workflow.cps.replay.ReplayCause"
  private static List<String> sf_ignoredFiles = new ArrayList<>(Arrays.asList(
      "CHANGELOG.md",
      "conventionalcommits.json",
      "LICENSE",
      "README.md",
  ))
  private Object m_steps
  private String m_jobName
  private String m_branch
  private boolean m_isReplay
  private List<String> m_commitMessages = new ArrayList<>()
  private List<String> m_modifiedFiles = new ArrayList<>()


  ShouldBuildJob(Object steps) {
    m_steps = steps
    m_jobName = m_steps.env.JOB_NAME
    if (m_steps.env.BRANCH_NAME) {
      // according to https://stackoverflow.com/a/37085196/1063501
      // env.BRANCH_NAME only available in multibranch pipeline
      m_branch = m_steps.env.BRANCH_NAME
    } else {
      m_branch = m_jobName.substring(m_jobName.indexOf("/") + 1)
    }
    m_isReplay = m_steps.currentBuild.rawBuild.getCauses()
        .any{ cause -> cause.toString().contains(sf_replayClassName) }

    // changeSets will always be empty during replay (https://issues.jenkins.io/browse/JENKINS-36453)
    boolean hasApiChange = false
    boolean hasWebsiteChange = false
    int numIgnored = 0
    StringBuilder commitBuilder = new StringBuilder()
    m_steps.echo "Reviewing ${m_steps.currentBuild.changeSets.size()} change set(s)"
    for (changeLogSet in m_steps.currentBuild.changeSets) {
      for (item in changeLogSet.getItems()) {
        String msg = item.msg
        if (msg != null) {
          msg = msg.trim()
          if (msg.length() > 0) {
            m_steps.echo "msg: ${msg}"
            m_commitMessages.add(msg)
          }
        }
        for (file in item.affectedFiles) {
          def path = file.path
          m_steps.echo "file: ${path}"
          if (path.startsWith(".idea/") ||
              path.endsWith(".iml") ||
              path.startsWith("src/") ||
              sf_ignoredFiles.contains(path)
          ) {
            numIgnored += 1
            continue
          }
          m_modifiedFiles.add(path)
          if (path.startsWith("pgkb-website/")) {
            hasWebsiteChange = true
          } else {
            hasApiChange = true
          }
        }
      }
    }
    if (!hasApiChange && !hasWebsiteChange && numIgnored == 0) {
      // is replay?
      if (m_isReplay) {
        m_steps.echo "IS REPLAY!  Forcing PGKB_DO_API and PGKB_DO_WEBSITE to true, PGKB_DO_STORYBOOK to false"
        hasApiChange = true
        hasWebsiteChange = true
      } else {
        m_steps.echo "Nothing to do!  Found ${numIgnored} skippable files..."
      }
    }
    m_steps.env.PGKB_DO_API = hasApiChange
    m_steps.env.PGKB_DO_WEBSITE = hasWebsiteChange
    m_steps.env.PGKB_DO_STORYBOOK = hasWebsiteChange && !m_isReplay &&
        (m_branch == "master" || m_branch == "development")
    m_steps.env.PGKB_COMMITS = String.join("\n", m_commitMessages)
    m_steps.env.IS_REPLAY = m_isReplay
    m_steps.echo "COMMITS:\n ${m_steps.env.PGKB_COMMITS}"
  }


  private boolean abort(String msg) {
    m_steps.currentBuild.result = Result.ABORTED
    m_steps.currentBuild.description = "ABORT: ${msg}"
    m_steps.error(msg)
    return false
  }


  boolean becauseDependabot() {
    if (m_branch.startsWith("dependabot")) {
      return abort("Ignore dependabot branches")
    }
    return true
  }


  boolean becausePullRequest() {
    if (m_branch.matches("PR-\\d+")) {
      return abort("Ignore pull requests")
    }
    return true
  }


  /**
   * Checks if all commits have {@code [skip ci]} in its message.
   * <p>
   * This will always return true during a replay (https://issues.jenkins.io/browse/JENKINS-36453).
   */
  boolean becauseSkipCi() {
    m_steps.echo "Checking for [skip ci]..."
    int numSkipCi = countSkipCi(m_commitMessages)

    if (numSkipCi > 0 && numSkipCi == m_commitMessages.size()) {
      return abort("All commits requested [skip ci]")
    }
    return true
  }


  /**
   * Checks if there is more than one job queued for this project/branch.
   * <p>
   * This will always return true duringa replay (https://issues.jenkins.io/browse/JENKINS-36453).
   */
  boolean becauseOutdated() {

    m_steps.echo "Checking if branch is up to date..."
    if (m_isReplay) {
      m_steps.echo "IS REPLAY! Skipping up-to-date check."
    }
    // confirm checked-out branch is the latest
    // must make git calls BEFORE Jenkins.get()
    m_steps.sh "git fetch"
    String rez = m_steps.sh(returnStdout: true, script: "git log --pretty=format:'%B-----' HEAD..origin/${m_branch}").trim()
    if (rez.length() > 0) {
      String[] futureCommits = rez.split("-----")
      m_steps.echo "This branch is ${futureCommits.length} commits behind origin/${m_branch}"
      for (msg in futureCommits) {
        m_steps.echo "future commit: '${msg}'"
      }
      // if all future commits are [skip ci], then proceed with build
      if (countSkipCi(futureCommits) == futureCommits.length) {
        m_steps.echo "All future commits are [skip ci]!"
        return true
      }
    } else {
      return true
    }

    m_steps.echo "Looking for newer build of '${m_jobName}' in queue..."
    List<Queue.Item> queueItems = Jenkins.get().getQueue().getItems()
    m_steps.echo "Found ${queueItems.size()} items in queue"
    for (item in queueItems) {
      String[] taskInfo = parseUrl(item.task.getUrl())
      if (taskInfo.length > 0) {
        String taskName = "${taskInfo[0]}/${taskInfo[1]}"
        m_steps.echo "Queued task = ${taskName}"
        if (taskName == m_jobName) {
          return abort("Newer ${m_jobName} job(s) in queue")
        }
      }
    }
    m_steps.echo "No newer ${m_jobName} job(s) in queue, proceeding..."
    return true
  }


  private static int countSkipCi(messages) {
    int numSkipCi = 0
    for (msg in messages) {
      if (msg.contains("[skip ci]")) {
        numSkipCi += 1
      } else {
        break
      }
    }
    return numSkipCi
  }


  // example: job/PharmGKB/job/development/1/
  private static Pattern sf_multibranchPipelineJobPattern = Pattern.compile("job/(.*?)/job/(.*?)/(\\d+)/")

  private static String[] parseUrl(String url) {
    Matcher m = sf_multibranchPipelineJobPattern.matcher(url)
    if (m.find()) {
      String[] rez = new String[2]
      rez[0] = m.group(1)
      rez[1] = m.group(2)
      return rez
    }
    return new String[0]
  }
}
