# Changelog

## [Unreleased]

## [1.0.1]

### Changed

- The filter and the *Source for all* and *Target for all* pickers now share the width of the dialog
  evenly. Making the dialog wider widens all three, where before the filter took everything

### Fixed

- *Refresh* no longer widens the dialog when a repository has long branch names. The full name is
  available as a tooltip instead, in both pickers as well as in the branch columns of the table

## [1.0.0]

First stable release. The versions below it were withdrawn from the Marketplace, so this section
describes what the plugin does rather than what changed since a version nobody has.

### Added

- Create merge requests for **all open projects** from a single dialog, one per selected repository
- **GitLab, GitHub, Gitea and Forgejo**, each on their hosted service as well as on your own server.
  Which host handles a repository is decided per repository, so one run can mix them
- A repository that fails never stops the others. Afterwards a result window lists every repository
  with its link or its error, and offers Open All, Copy Links and **Retry Failed**
- Title and description are templates with `{project}`, `{repo}`, `{branch}`, `{source}` and
  `{target}` placeholders, filled in per repository
- Built for many repositories at once: filter the table, set a source or target branch across all of
  them with type to search, override it per repository, and sort by any column
- Before the run, the dialog says which rows will not work: no remote, no configured host, no token,
  identical branches, a branch that was never pushed, or a request that already covers it. A double
  click opens the request that already exists
- **Refresh** fetches every repository without leaving the dialog
- Requests are created in parallel, configurable under *Repositories at a time*
- Requests follow the IDE's proxy settings, so the plugin works behind a company proxy
- Settings hold the defaults and one access token per host, stored in the IDE password safe.
  **Test Connection** names the account a token belongs to, and saving a host runs the same check
- Options a host cannot honour are switched off and the hosts are named. GitHub, Gitea and Forgejo
  decide squashing and branch deletion when merging, not when the request is created

## [0.3.0]

### Added

- GitHub, Gitea and Forgejo, alongside GitLab, each on their hosted service as well as on your own
  server
- The dialog asks every host whether a request already covers a row's branches. Such a row is
  unchecked and marked *Request already open*, and a double click opens the existing one
- Branches that exist only locally are called out as *Source branch not pushed yet*, so the run no
  longer fails on something that was visible beforehand
- **Refresh** fetches every repository and updates branches and statuses without leaving the dialog
- Type to search in *Source for all* and *Target for all*
- The dialog remembers its size and position

### Changed

- Status is colour coded: green runs, yellow has nothing left to do, red needs you
- The Project column appears only when more than one project is open
- *Repositories at a time* replaces *Parallel requests*, with an explanation of what the number buys
- The *for all* branch pickers are no longer editable. A branch none of the repositories has could
  not be applied anyway, and typing now searches the list instead

## [0.2.0]

### Added

- GitHub, Gitea and Forgejo, alongside GitLab, each on their hosted service as well as on your own
  server
- **Test Connection** in the settings, which names the account a token belongs to. Saving a host runs
  the same check and warns when it fails
- Filter, **Source for all** and **Target for all**, select and deselect all, and sortable columns, so
  a workspace with dozens of repositories stays workable
- A result window after a run that lists every repository with its link or its error, and offers Open
  All, Copy Links and **Retry Failed**
- Requests are created in parallel, configurable under *Repositories at a time*
- A separate Provider column, so Status only says why a row will or will not run

### Changed

- Options a host cannot honour are switched off and the hosts are named. GitHub, Gitea and Forgejo
  decide squashing and branch deletion when merging, not when the request is created
- The dialog no longer writes its values back to the saved defaults. What you type there applies to
  that run only
- *Delete source branch when merged* is on by default
- The token field shows a filler as long as the stored token, instead of a sentence saying one exists

### Fixed

- The action was registered in a Git menu group that is not visible in the default UI, so it could not
  be found
- Renaming a host orphaned its access token. The token now moves with the entry
- Hosts served over plain HTTP were unreachable because HTTPS was always assumed
- The credential store was read on the UI thread, which the platform reports and which can freeze the
  IDE
- The Kotlin runtime was bundled with the plugin, which can conflict with the one the IDE ships

## [0.1.0]

### Added

- Create merge requests for all open projects from a single dialog
- GitLab, both gitlab.com and self-managed instances
- Settings page with default target branch, title and description templates, and access tokens stored
  in the IDE password safe
