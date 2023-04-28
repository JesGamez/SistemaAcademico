CREATE table if not exists "USERS" (
    id identity not null,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    email varchar(255) not null,
    password varchar(255) not null
);

alter table if exists USERS
    add constraint if not exists uq_email unique (email);

create table if not exists TOKEN(
    id identity not null,
    refresh_token varchar(255) not null ,
    expired_at datetime not null ,
    issued_at datetime not null ,
    users bigint not null ,
    constraint fk_token_user foreign key (users) references  USERS (id)
);

create table if not exists password_recovery(
  id identity not null ,
  token varchar(255) not null ,
  users bigint not null ,
  constraint fk_password_recovery_user foreign key (users) references USERS (id)
);

