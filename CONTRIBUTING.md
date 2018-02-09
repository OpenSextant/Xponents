Consider Contributing OpenSextant
=================================

OpenSextant is our umbrella project, not a particular module.
The contributors are loosely affiliated, but largely volunteers.

If you have ideas it is best to contact the author, here for Xponents == Marc Ubaldino, mubaldino@gmail.com

If you have specific requests or bug fixes related to current APIs, please file an issue.

This is a research prototype, and consistently funded.  
So please have some patience and willingness to do some of the work.
Collaborators that have a sincere interest may be considered to join the group.

Maven Publishing
----------------

```

  # Fix all versions to be release versions.
  # Ensure GPG key is known...
  # and OSSRH login is set in settings.xml
  mvn clean deploy -P release
  mvn nexus-staging:release
```
