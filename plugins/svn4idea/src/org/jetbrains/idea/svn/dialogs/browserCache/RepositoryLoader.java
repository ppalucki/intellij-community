/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.auth.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.swing.*;
import java.util.*;

class RepositoryLoader extends Loader {
  // may be several requests if: several same-level nodes are expanded simultaneosly; or browser can be opening into some expanded state
  private final Queue<Pair<RepositoryTreeNode, Expander>> myLoadQueue;
  private boolean myQueueProcessorActive;

  RepositoryLoader(final SvnRepositoryCache cache) {
    super(cache);

    myLoadQueue = new LinkedList<Pair<RepositoryTreeNode, Expander>>();
    myQueueProcessorActive = false;
  }

  public void load(final RepositoryTreeNode node, final Expander afterRefreshExpander) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = Pair.create(node, afterRefreshExpander);
    if (! myQueueProcessorActive) {
      startLoadTask(data);
      myQueueProcessorActive = true;
    } else {
      myLoadQueue.offer(data);
    }
  }

  private void setResults(final Pair<RepositoryTreeNode, Expander> data, final List<DirectoryEntry> children) {
    myCache.put(data.first.getURL().toString(), children);
    refreshNode(data.first, children, data.second);
  }

  private void setError(final Pair<RepositoryTreeNode, Expander> data, final String message) {
    myCache.put(data.first.getURL().toString(), message);
    refreshNodeError(data.first, message);
  }

  private void startNext() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = myLoadQueue.poll();
    if (data == null) {
      myQueueProcessorActive = false;
      return;
    }
    if (data.first.isDisposed()) {
      // ignore if node is already disposed
      startNext();
    } else {
      startLoadTask(data);
    }
  }

  private void startLoadTask(final Pair<RepositoryTreeNode, Expander> data) {
    final ModalityState state = ModalityState.current();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().runProcess(new LoadTask(data), new EmptyProgressIndicator() {
          @NotNull
          @Override
          public ModalityState getModalityState() {
            return state;
          }
        });
      }
    });
  }

  public void forceRefresh(final String repositoryRootUrl) {
    // ? remove
  }

  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.REFRESHED;
  }

  private class LoadTask implements Runnable {
    private final Pair<RepositoryTreeNode, Expander> myData;

    private LoadTask(final Pair<RepositoryTreeNode, Expander> data) {
      myData = data;
    }

    public void run() {
      final Collection<DirectoryEntry> entries = new TreeSet<DirectoryEntry>();
      final RepositoryTreeNode node = myData.first;
      final SvnVcs vcs = node.getVcs();
      SvnAuthenticationProvider.forceInteractive();

      DirectoryEntryConsumer handler = new DirectoryEntryConsumer() {

        @Override
        public void consume(final DirectoryEntry entry) throws SVNException {
          entries.add(entry);
        }
      };
      try {
        SvnTarget target = SvnTarget.fromURL(node.getURL());
        vcs.getFactoryFromSettings().createBrowseClient().list(target, SVNRevision.HEAD, Depth.IMMEDIATES, handler);
      }
      catch (final VcsException e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            setError(myData, e.getMessage());
            startNext();
          }
        });
        return;
      } finally {
        SvnAuthenticationProvider.clearInteractive();
      }

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          setResults(myData, new ArrayList<DirectoryEntry>(entries));
          startNext();
        }
      });
    }
  }
}
