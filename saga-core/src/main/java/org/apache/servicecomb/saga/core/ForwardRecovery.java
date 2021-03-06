/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.saga.core;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForwardRecovery implements RecoveryPolicy {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public SagaResponse apply(SagaTask task, SagaRequest request, SagaResponse parentResponse) {
    try {
      for(int i = 0; isRetryable(i, request.transaction()); i++) {
        try {
          return request.transaction().send(request.serviceName(), parentResponse);
        } catch (Exception e) {
          log.error("Applying {} policy due to failure in transaction {} of service {}",
              description(),
              request.transaction(),
              request.serviceName(),
              e
          );
          Thread.sleep(request.failRetryDelayMilliseconds());
        }
      }
    } catch (InterruptedException ignored) {
      log.warn("Applying {} interrupted in transaction {} of service {}",
          description(),
          request.transaction(),
          request.serviceName(),
          ignored);
      throw new TransactionFailedException(ignored);
    }
    throw new TransactionAbortedException(
        String.format(
            "Too many failures in transaction %s of service %s, abort the transaction!",
            request.transaction(),
            request.serviceName()));
  }

  private boolean isRetryable(int i, Transaction transaction) {
    return transaction.retries() <= 0 || i <= transaction.retries();
  }

  @Override
  public String description() {
    return getClass().getSimpleName();
  }
}
