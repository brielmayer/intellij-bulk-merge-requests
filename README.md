# Bulk Merge Requests

Creates merge requests for **all your open projects** in one dialog.

Instead of visiting every repository in turn, you pick the source and target branch per repository in
a single table, following the layout of the IDE's own Push dialog, and confirm once. One merge
request is created per selected repository.

A repository that fails never stops the others. You get one summary at the end, with direct links to
everything that was created and a clear reason for everything that was not.

Supported today: **GitLab**, **GitHub**, **Gitea** and **Forgejo**, each on their hosted service as well as on
your own server.

<!-- Screenshot of the batch dialog. Replace docs/images/bulk-merge-requests-dialog.png with your
     own capture; keep the file name so this link keeps working. -->
![The Bulk Merge Requests dialog with one row per repository](docs/images/bulk-merge-requests-dialog.png)

## Requirements

* IntelliJ IDEA 2025.3 or newer, any edition with the bundled Git plugin
* An access token for your host:
  * GitLab: a personal access token with the `api` scope
  * GitHub: a personal access token that may create pull requests (classic: `repo`, fine grained: Pull requests,
    read and write)
  * Gitea or Forgejo: an access token with write access to the repository

## Getting started

### 1. Add your host

`Settings | Tools | Bulk Merge Requests`, then **+** under *Hosts and access tokens*.

| Field | |
|---|---|
| Host | `gitlab.com`, `github.com`, `codeberg.org`, or your own server such as `git.example.com`. Hosts of your open projects are already offered in the dropdown. Pasting a full URL works too, it gets cleaned up |
| Provider | GitLab, GitHub, Gitea or Forgejo |
| Access token | See the requirements above. In GitLab you create one under *Edit profile, Access Tokens*, in GitHub under *Settings, Developer settings*, in Gitea and Forgejo under *Settings, Applications* |

Your token is stored in the IDE password safe, the same place the IDE keeps your other credentials.
It is never written to a settings file and never appears in a log.

### 2. Create the merge requests

Open the projects you want to work with. One window with several repositories works just as well as
several windows.

Then `Git | Bulk Merge Requests…`, review the table, and confirm.

## The dialog

At the top you set what applies to every merge request: the title, an optional description, and
whether the source branch should be deleted and the commits squashed on merge.

Those last two only exist on GitLab. GitHub, Gitea and Forgejo decide both when a pull request is
merged, not when it is opened, so the checkboxes switch off and name the hosts that ignore them as
soon as your selection contains only such repositories.

The table below has one row per repository.

| | |
|---|---|
| **Source for all** / **Target for all** | Sets one branch across the board. Repositories that do not have that branch keep the one they had, so nothing is silently pointed at a branch that does not exist |
| **Filter** | Narrows the table by project, repository or branch name. *Select all* and *Deselect all* apply to what you currently see |
| **Status** | Says why a row cannot run |

Rows that cannot produce a merge request are greyed out and cannot be selected. The status column
tells you which of these applies:

| Status | What to do |
|---|---|
| No Git remote | The repository has no remote configured |
| No provider configured for this host | The host is missing from your settings. Use the link at the bottom of the dialog; the rows update as soon as you come back |
| No access token for this host | Same place, add the token |
| Source and target are identical | Pick a different branch in that row |

Click a branch cell to choose from that repository's branches. You can also type a name that does not
exist yet.

The button tells you what will happen: *Create 12 Merge Requests*.

## Titles and descriptions

Title and description are templates, filled in per repository. Available placeholders:

| Placeholder | |
|---|---|
| `{project}` | Name of the IDE project |
| `{repo}` | Name of the repository |
| `{branch}` | Source branch, same as `{source}` |
| `{source}` | Source branch |
| `{target}` | Target branch |

The default title is `Merge {branch} into {target}`.

Anything the plugin does not recognise is left as it is, so a typo stays visible instead of quietly
disappearing from your titles.

## After the run

A notification summarises the result, for example *Created 12 of 14 Merge Requests*.

If anything failed, a result window opens listing every repository with its link or its error
message. From there you can open all created merge requests at once, copy the links, or retry only
the ones that failed. Double clicking a row opens that merge request in your browser.

Common reasons a single repository fails:

* The source branch was never pushed, so the server does not know it
* A merge request for this branch combination already exists
* The token is missing the `api` scope, or has expired

## Settings

`Settings | Tools | Bulk Merge Requests`

| | |
|---|---|
| Default target branch | Preselected whenever a repository has a branch with that name |
| Title template | Default title, see the placeholders above |
| Description template | Default description, may stay empty |
| Delete source branch when merged | On by default |
| Squash commits when merging | Off by default |
| Parallel requests | How many merge requests are created at the same time. The default of 4 keeps a large batch fast; set it to 1 to send them strictly one after another |

The dialog remembers the title, description and the two checkboxes from your last run, so a workflow
you repeat is already set up the next time.

---

Brielmayer Consulting GmbH
