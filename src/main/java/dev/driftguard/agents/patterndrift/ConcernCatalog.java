package dev.driftguard.agents.patterndrift;

import java.util.List;

/**
 * Jobs a project normally does one way, and the common ways of doing them. Two of
 * these in one project is the classic sign of different AI sessions making different
 * choices. It's a hand-written list, so anything not listed simply isn't detected.
 */
final class ConcernCatalog {

    /**
     * @param modules imports that mean this approach is in use
     * @param callees calls that mean the same, for things with no import, like fetch
     * @param casual true for print and console: only worth counting in 2+ files next to a real alternative
     */
    record Approach(String name, List<String> modules, List<String> callees, boolean casual) {}

    record Concern(String id, String title, String language, List<Approach> approaches) {}

    private static Approach lib(String name, String... modules) {
        return new Approach(name, List.of(modules), List.of(), false);
    }

    private static Approach call(String name, String... callees) {
        return new Approach(name, List.of(), List.of(callees), false);
    }

    private static Approach casual(String name, String... callees) {
        return new Approach(name, List.of(), List.of(callees), true);
    }

    private static Concern py(String id, String title, Approach... approaches) {
        return new Concern(id, title, "python", List.of(approaches));
    }

    private static Concern js(String id, String title, Approach... approaches) {
        return new Concern(id, title, "javascript", List.of(approaches));
    }

    static final List<Concern> ALL = List.of(
            py("http-client", "HTTP clients",
                    lib("requests", "requests"), lib("httpx", "httpx"), lib("aiohttp", "aiohttp"),
                    lib("urllib", "urllib.request", "urllib3")),
            py("logging", "logging approaches",
                    lib("logging", "logging"), lib("loguru", "loguru"), lib("structlog", "structlog"),
                    lib("fastlogging", "fastlogging"), casual("print", "print")),
            py("validation", "validation libraries",
                    lib("pydantic", "pydantic"), lib("marshmallow", "marshmallow"), lib("cerberus", "cerberus"),
                    lib("voluptuous", "voluptuous"), lib("jsonschema", "jsonschema")),
            py("database", "database layers",
                    lib("SQLAlchemy", "sqlalchemy"), lib("SQLModel", "sqlmodel"), lib("Peewee", "peewee"),
                    lib("Tortoise", "tortoise"), lib("Django ORM", "django.db"), lib("raw psycopg", "psycopg2", "psycopg"),
                    lib("raw sqlite3", "sqlite3"), lib("raw pymysql", "pymysql")),
            py("password-hashing", "password hashing libraries",
                    lib("passlib", "passlib"), lib("bcrypt", "bcrypt"), lib("argon2", "argon2")),
            py("dates", "date libraries",
                    lib("arrow", "arrow"), lib("pendulum", "pendulum"), lib("dateutil", "dateutil")),

            js("http-client", "HTTP clients",
                    lib("axios", "axios"), lib("got", "got"), lib("node-fetch", "node-fetch"),
                    lib("superagent", "superagent"), lib("ky", "ky"), call("fetch", "fetch", "window.fetch")),
            js("logging", "logging approaches",
                    lib("winston", "winston"), lib("pino", "pino"), lib("bunyan", "bunyan"), lib("log4js", "log4js"),
                    casual("console", "console.log", "console.error", "console.warn", "console.info")),
            js("validation", "validation libraries",
                    lib("zod", "zod"), lib("yup", "yup"), lib("joi", "joi"), lib("ajv", "ajv"),
                    lib("class-validator", "class-validator"), lib("express-validator", "express-validator"),
                    lib("valibot", "valibot")),
            js("database", "database layers",
                    lib("Mongoose", "mongoose"), lib("Prisma", "@prisma/client"), lib("Sequelize", "sequelize"),
                    lib("TypeORM", "typeorm"), lib("Drizzle", "drizzle-orm"), lib("Knex", "knex"),
                    lib("raw pg", "pg"), lib("raw mysql", "mysql2", "mysql"), lib("raw mongodb", "mongodb")),
            js("password-hashing", "password hashing libraries",
                    lib("bcrypt", "bcrypt"), lib("bcryptjs", "bcryptjs"), lib("argon2", "argon2")),
            js("dates", "date libraries",
                    lib("moment", "moment"), lib("dayjs", "dayjs"), lib("date-fns", "date-fns"), lib("luxon", "luxon")),
            js("utilities", "utility libraries",
                    lib("lodash", "lodash", "lodash-es"), lib("underscore", "underscore"), lib("ramda", "ramda")),
            js("state", "state management libraries",
                    lib("Redux", "redux", "@reduxjs/toolkit", "react-redux"), lib("Zustand", "zustand"),
                    lib("MobX", "mobx"), lib("Jotai", "jotai"), lib("Recoil", "recoil"),
                    lib("Pinia", "pinia"), lib("Vuex", "vuex")),
            js("data-fetching", "data-fetching libraries",
                    lib("TanStack Query", "@tanstack/react-query", "react-query"), lib("SWR", "swr"),
                    lib("Apollo", "@apollo/client"), lib("RTK Query", "@reduxjs/toolkit/query")),
            js("forms", "form libraries",
                    lib("react-hook-form", "react-hook-form"), lib("Formik", "formik"), lib("react-final-form", "react-final-form")),
            js("toasts", "toast libraries",
                    lib("react-hot-toast", "react-hot-toast"), lib("react-toastify", "react-toastify"),
                    lib("sonner", "sonner"), lib("notistack", "notistack")),
            js("icons", "icon libraries",
                    lib("lucide", "lucide-react"), lib("react-icons", "react-icons"), lib("Heroicons", "@heroicons/react"),
                    lib("Font Awesome", "@fortawesome/react-fontawesome"), lib("Tabler", "@tabler/icons-react")),
            js("ui-kit", "UI component libraries",
                    lib("MUI", "@mui/material"), lib("Ant Design", "antd"), lib("Chakra", "@chakra-ui/react"),
                    lib("Mantine", "@mantine/core"), lib("React Bootstrap", "react-bootstrap")));

    private ConcernCatalog() {}
}
