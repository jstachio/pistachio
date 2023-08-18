Notes for gpg

I have no idea why but gpg on macos with pinentry-mac will sometimes just not work.

The following seems to wake up the agent:

```
gpg --use-agent --armor --detach-sign --output $(mktemp) pom.xml
```
