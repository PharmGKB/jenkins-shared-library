void call() {
  // according to https://stackoverflow.com/a/37085196/1063501
  // env.BRANCH_NAME only available in multibranch pipeline
  echo "BRANCH_NAME: ${env.BRANCH_NAME}"
  if (env.BRANCH_NAME != "master" && env.BRANCH_NAME != "development" && env.BRANCH_NAME != "main") {
    env.THROTTLE_CATEGORY = env.JOB_NAME.substring(0, env.JOB_NAME.lastIndexOf("/")) + "/feature_branch"
  } else {
    env.THROTTLE_CATEGORY = env.JOB_NAME
  }
  echo "THROTTLE_CATEGORY = ${env.THROTTLE_CATEGORY}"
}
