# Qlik CI/CD CLI

## Command Flows
```mermaid
flowchart TD

    subgraph main[qlik.cicd.core/main]
        main__input@{shape: event, label: "function --args"}
        main__ensure_env_map@{shape: prepare, label: "qlik.cicd.core/ensure-env-map"}
        main__parse_args@{shape: process, label: "Parse args"}

        main__input --> main__ensure_env_map --> main__parse_args
    end
    
    subgraph init[qlik.cicd.core/init]
        direction TB
        init__input@{shape: event, label: "env\napp-name\napp-usage\ntarget-space"}

        subgraph app_exists["qlik.cicd.utilities/app-exists?"]
            direction TB

            app_exists__input@{shape: event, label: "env\napp-name\nspace-name"}
            
            subgraph get_space_id["qlik.cicd.api/get-space-id"]
                get_space_id__input@{shape: event, label: "env\nspace-name"}
                endpoint__space@{shape: terminal, label: "API"}

                get_space_id__input --> endpoint__space
            end

            is_space_id_null@{shape: decision, label: "nil?"}
            app_exists__return__false__space@{shape: odd, label: "False"}

            subgraph get_app_id["qlik.cicd.api/get-app-id"]
                get_app_id__input@{shape: event, label: "env\napp-name\nspace-id"}
                endpoint__app@{shape: terminal, label: "API"}

                get_app_id__input --> endpoint__app
            end

            is_app_id_null@{shape: decision, label: "nil?"}

            app_exists__return__true@{shape: odd, label: "True"}
            app_exists__return__false__app@{shape: odd, label: "False"}

            app_exists__input --> get_space_id
            get_space_id --> is_space_id_null
            is_space_id_null -->|Yes| app_exists__return__false__space
            is_space_id_null -->|No| get_app_id
            get_app_id --> is_app_id_null
            is_app_id_null -->|Yes| app_exists__return__false__app
            is_app_id_null -->|No| app_exists__return__true

        end

        init__app_exists__feature@{shape: prepare, label: "qlik.cicd.utilities/app-exists?\n(feature)"}
        init__use_space__feature@{shape: prepare, label: "Use  feature space"}
        init__create_app@{shape: prepare, label: "Create app in feature space"}
        init__return__success@{shape: odd, label: "Return app ID"}        
        init__return__failure__target@{shape: terminal, label: "Raise error"}
        init__return__failure__feature@{shape: terminal, label: "Raise error"}

        init__input --> app_exists
        app_exists -->|False| init__app_exists__feature
        app_exists -->|True| init__return__failure__target
        init__app_exists__feature -->|False| init__use_space__feature
        init__app_exists__feature -->|True| init__return__failure__feature
        init__use_space__feature --> init__create_app
        init__create_app --> init__return__success

    end

    subgraph pull[qlik.cicd.core/pull]
    end

    subgraph push[qlik.cicd.core/push]
    end

    subgraph deploy[qlik.cicd.core/deploy]
    end

    subgraph purge[qlik.cicd.core/purge]
    end


    main --> init --> init__pull@{shape: prepare, label: "qlik.cicd.core/init"}
    main --> pull
    main --> push
    main --> deploy
    main --> purge
```