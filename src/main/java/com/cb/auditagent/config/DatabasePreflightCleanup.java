package com.cb.auditagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component("databasePreflightCleanup")
public class DatabasePreflightCleanup {
    private static final Logger logger = LoggerFactory.getLogger(DatabasePreflightCleanup.class);

    private static final List<String> CLEANUP_QUERIES = List.of(
            // Remediation child entities referencing agent_runs
            "DELETE FROM agent_steps WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM verification_results WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM run_changes WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM subagent_state WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM run_publications WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM graph_node_events WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",
            "DELETE FROM graph_checkpoints WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",

            // Checkpoint events referencing graph_checkpoints
            "DELETE FROM graph_node_events WHERE checkpoint_id IS NOT NULL AND checkpoint_id NOT IN (SELECT checkpoint_id FROM graph_checkpoints)",

            // Memory and runs referencing memory_threads
            "DELETE FROM memory_messages WHERE thread_id IS NOT NULL AND thread_id NOT IN (SELECT thread_id FROM memory_threads)",
            "DELETE FROM agent_runs WHERE thread_id IS NOT NULL AND thread_id NOT IN (SELECT thread_id FROM memory_threads)",

            // Messages referencing agent_runs (nullify if run doesn't exist)
            "UPDATE memory_messages SET run_id = NULL WHERE run_id IS NOT NULL AND run_id NOT IN (SELECT run_id FROM agent_runs)",

            // Runs referencing vulnerabilities (nullify or delete if invalid)
            "DELETE FROM agent_runs WHERE vulnerability_id IS NOT NULL AND vulnerability_id NOT IN (SELECT id FROM vulnerabilities)",

            // User child entities referencing app_users
            "DELETE FROM user_sessions WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT user_id FROM app_users)",
            "DELETE FROM github_authorizations WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT user_id FROM app_users)",
            "DELETE FROM user_notifications WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT user_id FROM app_users)",

            // Repository child entities referencing managed_repositories
            "DELETE FROM scan_snapshots WHERE repository_id IS NOT NULL AND repository_id NOT IN (SELECT repository_id FROM managed_repositories)",
            "DELETE FROM scan_history_stats WHERE repository_id IS NOT NULL AND repository_id NOT IN (SELECT repository_id FROM managed_repositories)",
            "UPDATE run_publications SET repository_id = NULL WHERE repository_id IS NOT NULL AND repository_id NOT IN (SELECT repository_id FROM managed_repositories)",
            "UPDATE run_publications SET user_id = NULL WHERE user_id IS NOT NULL AND user_id NOT IN (SELECT user_id FROM app_users)"
    );

    public DatabasePreflightCleanup(DataSource dataSource) {
        runCleanup(dataSource);
    }

    private void runCleanup(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : CLEANUP_QUERIES) {
                try {
                    int rows = stmt.executeUpdate(sql);
                    if (rows > 0) {
                        logger.info("Pre-flight migration cleaned {} orphaned rows via: {}", rows, sql);
                    }
                } catch (Exception e) {
                    // Suppress error if table/column does not exist yet during initial boot
                    logger.debug("Pre-flight cleanup statement skipped (tables might not exist yet): {}", sql);
                }
            }
        } catch (Exception e) {
            logger.warn("Pre-flight database connection check completed with notice: {}", e.getMessage());
        }
    }

    @Component
    public static class PreflightBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                BeanDefinition bd = beanFactory.getBeanDefinition("entityManagerFactory");
                String[] dependsOn = bd.getDependsOn();
                List<String> list = dependsOn != null ? new ArrayList<>(Arrays.asList(dependsOn)) : new ArrayList<>();
                if (!list.contains("databasePreflightCleanup")) {
                    list.add("databasePreflightCleanup");
                    bd.setDependsOn(list.toArray(new String[0]));
                }
            }
        }
    }
}

