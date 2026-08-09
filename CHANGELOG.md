# Changelog

## [Unreleased]

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
