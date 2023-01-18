## Sample 5G-MAG Player
This is an application for initial testing purposes which integrates an instance of Exoplayer and a hard-coded URL.

### Testing with AS
The app can be tested as is against the following ContentHostingConfiguration_tv_pull-ingest.json:
```
{
  "name": "playlist",
  "ingestConfiguration": {
    "pull": true,
    "protocol": "urn:3gpp:5gms:content-protocol:http-pull-ingest",
    "entryPoint": "https://euronews-euronews-spanish-2-mx.samsung.wurl.com/"
  },
  "distributionConfigurations": [
    {
      "canonicalDomainName": "10.0.2.2",
      "domainNameAlias": "",
      "pathRewriteRules": [
	{
	  "requestPattern": "^/m4d/provisioning-session-[^/]*/",
	  "mappedPath": "/manifest/"
	}
      ]
    }
  ]
}
```
