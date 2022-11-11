# 5G-MAG Reference Tools: Sample 5G-MAG Player

Only for initial testing purposes

## Testing with AS
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
