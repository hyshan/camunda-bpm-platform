/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.test.concurrency;

import java.util.List;
import java.util.logging.Logger;

import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.impl.cmd.CompleteTaskCmd;
import org.camunda.bpm.engine.impl.cmd.SignalCmd;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.concurrency.CompetingSubprocessCompletionTest.CompleteTaskThread;


/**
 * @author Tom Baeyens
 */
public class CompetingProcessCompletionTest extends PluggableProcessEngineTestCase {

  private static Logger log = Logger.getLogger(CompetingSignalsTest.class.getName());

  static ControllableThread activeThread;

  public class CompleteTaskThread extends ControllableThread {
    String taskId;
    OptimisticLockingException exception;
    public CompleteTaskThread(String taskId) {
      this.taskId = taskId;
    }
    public synchronized void startAndWaitUntilControlIsReturned() {
      activeThread = this;
      super.startAndWaitUntilControlIsReturned();
    }
    public void run() {
      try {
        processEngineConfiguration
          .getCommandExecutorTxRequired()
          .execute(new ControlledCommand(activeThread, new CompleteTaskCmd(taskId, null)));

      } catch (OptimisticLockingException e) {
        this.exception = e;
      }
      log.fine(getName()+" ends");
    }
  }

  /**
   * This test requires a minimum of three concurrent executions to avoid
   * that all threads attempt compaction by which synchronization happens "by accident"
   */
  @Deployment
  public void testCompetingEnd() throws Exception {
    runtimeService.startProcessInstanceByKey("CompetingEndProcess");

    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(3, tasks.size());

    log.fine("test thread starts thread one");
    CompleteTaskThread threadOne = new CompleteTaskThread(tasks.get(0).getId());
    threadOne.startAndWaitUntilControlIsReturned();

    log.fine("test thread continues to start thread two");
    CompleteTaskThread threadTwo = new CompleteTaskThread(tasks.get(1).getId());
    threadTwo.startAndWaitUntilControlIsReturned();

    log.fine("test thread notifies thread 1");
    threadOne.proceedAndWaitTillDone();
    assertNull(threadOne.exception);

    log.fine("test thread notifies thread 2");
    threadTwo.proceedAndWaitTillDone();
    assertNotNull(threadTwo.exception);
    assertTextPresent("was updated by another transaction concurrently", threadTwo.exception.getMessage());
  }

}
