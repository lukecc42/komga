alter table media
    add column page_count bigint default 0;

update media m
set page_count = (select count(p.BOOK_ID) from media_page p where p.BOOK_ID = m.BOOK_ID);
