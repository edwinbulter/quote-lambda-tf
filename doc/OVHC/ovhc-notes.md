### Create Application Token

1. Go to: https://auth.eu.ovhcloud.com/api/createToken
2. Fill in the form:
   - Application Name: `quote-backend-ovhcloud`
   - Description: `Learning project implementation`
   - Callback URL: `https://localhost/oauth/callback`
   - Save the **Application Key**, **Application Secret**, and **Consumer Key**

### Get List of Applications with API calls

1. Go to: https://eu.api.ovh.com/console
2. Search for v1 /me
3. In the next search box type: GET /me/api/application
4. Click on Get /me/api/application
5. Click on Authenticate and SSO till you get back to the execute request screen
6. Click on Execute
7. You get a list with IDs that you can use with GET /ma/api/application/{applicationId}

### Create API key in console

1. Open Control panel: https://auth.eu.ovhcloud.com/signin/
2. Click on your profile button: opens https://manager.eu.ovhcloud.com/#/account/useraccount/dashboard
3. In left panel click on **>** 'Identity, Security & Operations'
4. In the submenu click on 'API keys'
5. Now you see a table with all API keys
6. Click on 'Create API key': opens https://auth.eu.ovhcloud.com/api/createToken
   7. Fill in the form:
      - Application Name: `quote-backend`
      - Description: `Quote Backend`
      - Validity: unlimited
      - Rights: 
         ```text
         GET /me
         POST /domain/*
      
         GET /cloud/*
         POST /cloud/*
         PUT /cloud/*
         PATCH /cloud/*
         DELETE /cloud/*
      
         GET /hosting/web/*
         POST /hosting/web/*
         PUT /hosting/web/*
         PATCH /hosting/web/*
         DELETE /hosting/web/*
      
         GET /database/*
         POST /database/*
         PUT /database/*
         PATCH /database/*
         DELETE /database/*
         ```
      - Save the **Application Key**, **Application Secret**, and **Consumer Key**

### quote-storage
s3 endpoint: https://s3.gra.io.cloud.ovh.net/



