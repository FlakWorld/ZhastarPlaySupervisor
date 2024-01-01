package controllers;

import controllers.admin.Accounts;
import controllers.admin.MonitoringReports;
import enums.ParentsItemType;
import enums.SpecialistsItemType;
import models.*;
import play.Play;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.db.jpa.JPA;
import play.i18n.Lang;
import play.mvc.Before;
import play.mvc.Controller;
import utils.BCrypt;
import utils.UserUtils;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Application extends Controller {

    static final String FILE_DIRECTORY =
            Play.configuration.getProperty("zhastar.public.folder.path");

    @Before
    public static void notLoggedIn() {
        String locale = Lang.get();
        if (locale == null || locale.equals("")) {
            locale = "ru";
        }
        renderArgs.put("locale", locale);
        renderArgs.put("currUrl", request.url);
        renderArgs.put("notLoggedIn", true);
    }

    // Set the default language to English
    static {
        Lang.set("ru");
    }

    /**
     * Renders the first page of the application.
     */
    public static void index() {
        renderArgs.put("home", true);
        render();
    }

    /**
     * Changes the language.
     *
     * @param locale  the language
     * @param currUrl the current url
     */
    public static void changeLocale(String locale, String currUrl) {
        Lang.change(locale);
        redirect(currUrl);
    }

    /**
     * Signs in or renders login page if the user is not logged in.
     */
    public static void login() {
        renderArgs.put("message", flash.get("error"));
        if (session.get("logged") == null) {
            renderArgs.put("headerContent", true);
            render();
        } else {
            Pages.contentDashboard(null, null);
        }
    }

    public static void registration() {
        if (flash.get("error") != null) {
            renderArgs.put("error", true);
        }

        render();
    }

    /**
     * Authenticates to the system.
     *
     * @param username the username
     * @param password the password
     */
    public static void authenticate(@Required String username,
                                    @Required String password) {
        checkAuthenticity();
        if (Validation.hasErrors()) {
            flash.put("error", "Укажите логин и пароль правильно");
            login();
        }
        Account account = Account.find("username = ?1", username).first();
        if (account == null) {
            flash.put("error", "Неправильный логин, проверьте ваш логин и попробуйте еще раз");
            login();
        }

        /*if (account.school != null && account.school.region != null && account.school.region.id == 5) {
            // akmola psychologists
            flash.put("error", "Доступ закрыт в вашем регионе");
            login();
            if (!account.username.equals("kairatov.kuat") && !account.username.equals("beisembaeva.ayazhan") &&
                    !account.username.equals("sabieva.ayman") && !account.username.equals("krylova.tatiana") &&
                    !account.username.equals("tursumbaeva.almira") && !account.username.equals("agaidarova.arailym") &&
                    !account.username.equals("g.serik") && !account.username.equals("t.kolupaiko") &&
                    !account.username.equals("muhanbetzhanova.aiym") && account.school.province.id != 40 && account.school.province.id != 50) {
                flash.put("error", "Доступ закрыт в вашем регионе");
                login();
            }
        }*/

        if (!BCrypt.checkpw(password, account.password)) {
            flash.put("error", "Неправильный пароль");
            login();
        }
        session.put("logged", username);

        SignInHistory signInHistory = new SignInHistory();
        signInHistory.account = account;
        signInHistory.createdAt = new Date();
        signInHistory.save();

        Profile profile = Profile.find("account = ?1", account).first();
        if (profile == null) {
            Settings.profile();
        }

        Pages.contentDashboard(null, null);
    }

    /**
     * Login for admins.
     */
    public static void adminLogin() {
        String errorMessage = flash.get("error");
        String active = "login";
        if (session.get("adminLogged") != null && session.get("regionalAdmin") == null) {
            Accounts.accounts(1, null, null,
                    null, null, null, null, null, null);
        }
        if (session.get("adminLogged") != null && session.get("regionalAdmin") != null) {
            MonitoringReports.reports();
        }
        renderTemplate("admin/auth/login.html", active, errorMessage);
    }

    /**
     * Gets the current logged admin.
     *
     * @return current admin
     */
    public static AdminUser getCurrentUser() {
        String email = session.get("adminEmail");
        AdminUser adminUser = AdminUser.find("email = ?", email).first();
        return adminUser;
    }

    /**
     * Authentication for the admins.
     *
     * @param email    the email
     * @param password the password
     */
    public static void adminAuthenticate(String email, String password) {
        checkAuthenticity();
        if (email != null) {
            AdminUser admin = AdminUser.find("email = ?", email).first();
            if (admin != null) {
                if (BCrypt.checkpw(password, admin.password)) {
                    session.put("adminLogged", admin);
                    session.put("adminEmail", email);
                    renderArgs.put("userLogged", admin);
                    if (email.equals("regionalAdmin")) {
                        session.put("regionalAdmin", true);
                        MonitoringReports.reports();
                    } else {
                        Accounts.accounts(1, null, null, null,
                                null, null, null, null, null);
                    }
                }
            }
        }
        String errorMessage = "Имя пользователя и пароль не совпадают.\n";
        flash.put("error", errorMessage);
        adminLogin();
    }

    /**
     * Sign out for the admins.
     */
    public static void adminLogout() {
        session.remove("adminLogged");
        session.remove("adminEmail");
        session.remove("regionalAdmin");
        adminLogin();
    }

    /**
     * Renders parents page (old).
     */
    public static void parents() {
        renderArgs.put("headerContent", true);
        renderArgs.put("username", session.get("logged"));
        render();
    }

    /**
     * Renders about page.
     */
    public static void about() {
        renderArgs.put("headerContent", true);
        renderArgs.put("username", session.get("logged"));
        List<AboutReport> reports = AboutReport.find("isActive = true").fetch();
        render(reports);
    }

    /**
     * Renders program page.
     */
    public static void program() {
        renderArgs.put("headerContent", true);
        renderArgs.put("username", session.get("logged"));
        List<AboutReport> reports = AboutReport.find("isActive = true").fetch();
        List<Program> programs = Program.findAll();
        render(reports, programs);
    }

    /**
     * Renders programComment page.
     */
    public static void programComment(Integer id) {
        renderArgs.put("headerContent", true);
        renderArgs.put("username", session.get("logged"));
        List<AboutReport> reports = AboutReport.find("isActive = true").fetch();
        Program program = Program.findById(id);
        render(reports, program);
    }

    /**
     * Save comments of Program.
     *
     * @param id the id of the program
     */
    public static void sendComment(Integer id, String username, String comment) {
        renderArgs.put("headerContent", true);
        renderArgs.put("username", session.get("logged"));
        List<AboutReport> reports = AboutReport.find("isActive = true").fetch();
        Program program = Program.findById(id);
        ProgramComment newComment = new ProgramComment();
        newComment.comment = comment;
        newComment.username = username;
        newComment.program = program;
        newComment.save();
        programComment(id);
    }

    /**
     * Generates file containing reports.
     *
     * @param id the id of the report
     */
    public static void aboutReport(Integer id) {
        AboutReport report = AboutReport.findById(id);

        if (report == null) {
            notFound();
        }

        report.views++;
        report.save();

        redirect(report.url);
    }

    /**
     * Sign out for the users.
     */
    public static void logout() {
        session.remove("logged");
        index();
    }

    /**
     * Renders students page.
     * Author: Miras Kenzhegaliyev
     */
    public static void studentsPage() {
        List<StudentLifeSkill> videos = StudentLifeSkill.findAll();
        render(videos);
    }

    /**
     * Renders specialists page (new).
     *
     * @param id       the id of the specialists group
     * @param itemType item type of the specialists group
     */
    public static void specialistsPage(Integer id, SpecialistsItemType itemType) {
        List<SpecialistsGroup> currentSpecialistsGroup = new ArrayList<>();

        if (id == null && itemType == null) {
            List<SpecialistsGroup> allSpecialistsGroup = JPA.em().createQuery("Select p from "
                            + "SpecialistsGroup p order by p.position asc, p.id asc",
                    SpecialistsGroup.class).getResultList();

            currentSpecialistsGroup.addAll(allSpecialistsGroup);
        } else if (id != null) {
            currentSpecialistsGroup.add((SpecialistsGroup) SpecialistsGroup.findById(id));
        } else {
            currentSpecialistsGroup = SpecialistsGroup
                    .find("itemType = ?1 order by position asc, id asc", itemType).fetch();
        }

        List<SpecialistsGroup> specialistsGroups = JPA.em().createQuery("Select p from "
                        + "SpecialistsGroup p order by p.position asc, p.id asc",
                SpecialistsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (SpecialistsGroup specialistsGroup : specialistsGroups) {
            if (specialistsGroup.itemType.equals(SpecialistsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        render(specialistsGroups, currentSpecialistsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook);
    }

    public static void specialistsVideo(Integer id) {
        List<SpecialistsGroup> currentSpecialistsGroup = new ArrayList<>();

        List<SpecialistsGroup> specialistsGroups = JPA.em().createQuery("Select p from "
                        + "SpecialistsGroup p order by p.position asc, p.id asc",
                SpecialistsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (SpecialistsGroup specialistsGroup : specialistsGroups) {
            if (specialistsGroup.itemType.equals(SpecialistsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        SpecialistsItem specialistsItem = SpecialistsItem.findById(id);

        specialistsItem.views++;
        specialistsItem.save();

        render(specialistsGroups, currentSpecialistsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook, specialistsItem);
    }

    /**
     * Renders  specialists content view.
     */
    public static void specialistsContentView(Integer id) {
        List<SpecialistsGroup> currentSpecialistsGroup = new ArrayList<>();

        List<SpecialistsGroup> specialistsGroups = JPA.em().createQuery("Select p from "
                        + "SpecialistsGroup p order by p.position asc, p.id asc",
                SpecialistsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (SpecialistsGroup specialistsGroup : specialistsGroups) {
            if (specialistsGroup.itemType.equals(SpecialistsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (specialistsGroup.itemType.equals(SpecialistsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        SpecialistsItem specialistsItem = SpecialistsItem.findById(id);

        specialistsItem.views++;
        specialistsItem.save();

        render(specialistsGroups, currentSpecialistsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook, specialistsItem);
    }

    /**
     * Renders parents page (new).
     *
     * @param id       the id of the parents group
     * @param itemType item type of the parents group
     */
    public static void parentsPage(Integer id, ParentsItemType itemType) {
        List<ParentsGroup> currentParentsGroup = new ArrayList<>();

        if (id == null && itemType == null) {
            List<ParentsGroup> allParentsGroup = JPA.em().createQuery("Select p from "
                            + "ParentsGroup p order by p.position asc, p.id asc",
                    ParentsGroup.class).getResultList();

            currentParentsGroup.addAll(allParentsGroup);
        } else if (id != null) {
            currentParentsGroup.add((ParentsGroup) ParentsGroup.findById(id));
        } else {
            currentParentsGroup = ParentsGroup
                    .find("itemType = ?1 order by position asc, id asc", itemType).fetch();
        }

        List<ParentsGroup> parentsGroups = JPA.em().createQuery("Select p from "
                        + "ParentsGroup p order by p.position asc, p.id asc",
                ParentsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (ParentsGroup parentsGroup : parentsGroups) {
            if (parentsGroup.itemType.equals(ParentsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        render(parentsGroups, currentParentsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook);
    }

    public static void parentsVideo(Integer id) {
        List<ParentsGroup> currentParentsGroup = new ArrayList<>();

        List<ParentsGroup> parentsGroups = JPA.em().createQuery("Select p from "
                        + "ParentsGroup p order by p.position asc, p.id asc",
                ParentsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (ParentsGroup parentsGroup : parentsGroups) {
            if (parentsGroup.itemType.equals(ParentsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        ParentsItem parentsItem = ParentsItem.findById(id);

        parentsItem.views++;
        parentsItem.save();

        render(parentsGroups, currentParentsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook, parentsItem);
    }

    /**
     * Renders parents content view.
     */
    public static void parentsContentView(Integer id) {
        List<ParentsGroup> currentParentsGroup = new ArrayList<>();

        List<ParentsGroup> parentsGroups = JPA.em().createQuery("Select p from "
                        + "ParentsGroup p order by p.position asc, p.id asc",
                ParentsGroup.class).getResultList();

        boolean isThereVideo = false;
        boolean isThereArticle = false;
        boolean isThereMassMedia = false;
        boolean isThereInfographic = false;
        boolean isThereBook = false;

        for (ParentsGroup parentsGroup : parentsGroups) {
            if (parentsGroup.itemType.equals(ParentsItemType.VIDEO)) {
                isThereVideo = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.ARTICLE)) {
                isThereArticle = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.MASS_MEDIA)) {
                isThereMassMedia = true;
            } else if (parentsGroup.itemType.equals(ParentsItemType.INFOGRAPHIC)) {
                isThereInfographic = true;
            } else {
                isThereBook = true;
            }
        }

        ParentsItem parentsItem = ParentsItem.findById(id);

        parentsItem.views++;
        parentsItem.save();

        render(parentsGroups, currentParentsGroup, isThereVideo, isThereArticle, isThereMassMedia,
                isThereInfographic, isThereBook, parentsItem);
    }

    /**
     * Generates file containing parents item.
     *
     * @param parentsItemId the id of parents item
     * @param lang          the language
     */
    public static void openParentsItemDocument(Integer parentsItemId, String lang) {
        ParentsItem parentsItem = ParentsItem.findById(parentsItemId);

        if (parentsItem == null) {
            notFound();
        }

        final File documentFile;
        if (lang != null && "kz".equals(lang) && parentsItem.documentKz != null) {
            documentFile = new File(FILE_DIRECTORY, parentsItem.documentKz);
        } else if (lang != null && "kz".equals(lang) && parentsItem.documentRu != null) {
            documentFile = new File(FILE_DIRECTORY, parentsItem.documentRu);
        } else {
            parentsItem.views++;
            parentsItem.save();
            documentFile = new File(FILE_DIRECTORY, parentsItem.url);
        }
        if (!documentFile.exists()) {
            notFound();
        }
        final String fileName = documentFile.getName();
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        if (contentType == null || contentType.isEmpty()) {
            contentType = "text/plain";
        }

        response.setHeader("Content-Type", contentType);
        response.setHeader("Content-Disposition", "inline;filename=\""
                + parentsItem.name + "\"");
        renderBinary(documentFile);
    }

    /**
     * Counter for the video watching in parent's page.
     *
     * @param id the id of the parents item
     */
    public static void countVideoWatch(Integer id) {
        ParentsItem parentsItem = ParentsItem.findById(id);
        parentsItem.views++;
        parentsItem.save();
    }

    /**
     * Counter for the video watching in student's page.
     *
     * @param id the id of the parents item
     */
    public static void countStudentVideoWatch(Integer id) {
        StudentLifeSkill studentLifeSkill = StudentLifeSkill.findById(id);
        studentLifeSkill.views++;
        studentLifeSkill.save();
    }

    /**
     * Redirects to mass media.
     *
     * @param id  the id of the parents item
     * @param url redirect url
     */
    public static void massMediaLink(Integer id, String url) {
        ParentsItem parentsItem = ParentsItem.findById(id);
        parentsItem.views++;
        parentsItem.save();

        redirect(url);
    }

    public static void aboutLifeSkills() {
        Account user = Account.find("username = ?1", session.get("logged")).first();
        renderArgs.put("active", "lifeSkillsAbout");
        renderArgs.put("user", user);
        renderArgs.put("username", session.get("logged"));

        if (user != null) {
            renderArgs.put("courses", Course.find("group = ?1 and deletedAt = null and level < 1000", user.group).fetch());
            renderArgs.put("allowedCourseUserIds", UserUtils.allowedCourse3UserIds());
            renderArgs.put("allowedCourse4Usernames", UserUtils.allowedCourse4Usernames());
        }

        renderTemplate("Pages/aboutLifeSkills.html");
    }

    public static void resultsProgramSuicide() {
        renderArgs.put("active", "resultsSuicide");
        Account user = Account.find("username = ?1", session.get("logged")).first();
        renderArgs.put("user", user);
        renderArgs.put("username", session.get("logged"));

        if (user != null) {
            renderArgs.put("courses", Course.find("group = ?1 and deletedAt = null and level < 1000", user.group).fetch());
            renderArgs.put("allowedCourseUserIds", UserUtils.allowedCourse3UserIds());
            renderArgs.put("allowedCourse4Usernames", UserUtils.allowedCourse4Usernames());
        }

        renderTemplate("Pages/resultsSuicide.html");
    }

    public static void aboutProgramSuicide() {
        Account user = Account.find("username = ?1", session.get("logged")).first();
        renderArgs.put("active", "aboutSuicide");
        renderArgs.put("user", user);
        renderArgs.put("username", session.get("logged"));

        if (user != null) {
            renderArgs.put("courses", Course.find("group = ?1 and deletedAt = null and level < 1000", user.group).fetch());
            renderArgs.put("allowedCourseUserIds", UserUtils.allowedCourse3UserIds());
            renderArgs.put("allowedCourse4Usernames", UserUtils.allowedCourse4Usernames());
        }

        renderTemplate("Pages/aboutSuicide.html");
    }

    public static void resultsLifeSkills() {
        Account user = Account.find("username = ?1", session.get("logged")).first();
        renderArgs.put("active", "lifeSkillsResults");
        renderArgs.put("user", user);
        renderArgs.put("username", session.get("logged"));

        if (user != null) {
            renderArgs.put("courses", Course.find("group = ?1 and deletedAt = null and level < 1000", user.group).fetch());
            renderArgs.put("allowedCourseUserIds", UserUtils.allowedCourse3UserIds());
            renderArgs.put("allowedCourse4Usernames", UserUtils.allowedCourse4Usernames());
        }

        renderTemplate("Pages/resultsLifeSkills.html");
    }

    public static void supervisor() {
        render();
    }
}
