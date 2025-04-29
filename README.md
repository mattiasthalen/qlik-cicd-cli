# Qlik CI/CD CLI

## Command Flows
```mermaid
flowchart TD
    main@{shape: prepare, label: "qlik.cicd.core/main"}
    
    subgraph init[qlik.cicd.core/init]
    end

    subgraph pull[qlik.cicd.core/pull]
    end

    subgraph push[qlik.cicd.core/push]
    end

    subgraph deploy[qlik.cicd.core/deploy]
    end

    subgraph purge[qlik.cicd.core/purge]
    end

    main --> init
    main --> pull
    main --> push
    main --> deploy
    main --> purge
```