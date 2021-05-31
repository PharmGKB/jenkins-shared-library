package org.pharmgkb.jenkins

import hudson.AbortException


/**
 * Exception to distinguish abort due to newer jobs in queue.
 */
class CancelJobException extends AbortException {
  public CancelJobException(String message) {
    super(message)
  }
}
