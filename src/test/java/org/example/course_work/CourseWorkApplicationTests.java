package org.example.course_work;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ui.Model;
import jakarta.servlet.http.HttpSession;

import java.util.*;

class AuthControllerTest {

	@InjectMocks
	private AuthController authController;

	@Mock
	private LoginRepository loginRepository;

	@Mock
	private HttpSession session;

	@Mock
	private Model model;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testLogin_ValidUser_RedirectsToEvents() {
		// Arrange
		String username = "testUser";
		String password = "password";
		Login mockUser = new Login();
		mockUser.setUser(username);
		mockUser.setPassword(password);
		mockUser.setRole("user");

		when(loginRepository.findByUserAndPassword(username, password)).thenReturn(Optional.of(mockUser));

		// Act
		String result = authController.login(username, password, model, session);

		// Assert
		assertEquals("redirect:/events", result);
		verify(session).setAttribute("user", username);
		verify(session).setAttribute("role", "user");
	}

	@Test
	void testLogin_InvalidUser_ReturnsLoginWithError() {
		// Arrange
		String username = "invalidUser";
		String password = "wrongPassword";

		when(loginRepository.findByUserAndPassword(username, password)).thenReturn(Optional.empty());

		// Act
		String result = authController.login(username, password, model, session);

		// Assert
		assertEquals("login", result);
		verify(model).addAttribute("error", "Неправильный логин или пароль.");
	}

	@Test
	void testRegister_NewUser_RedirectsToEvents() {
		// Arrange
		String username = "newUser";
		String password = "password";

		when(loginRepository.findByUser(username)).thenReturn(Optional.empty());

		// Act
		String result = authController.register(username, password, model, session);

		// Assert
		assertEquals("redirect:/events", result);
		verify(loginRepository).save(any(Login.class));
		verify(session).setAttribute("user", username);
	}

	@Test
	void testRegister_ExistingUser_ReturnsRegisterWithError() {
		// Arrange
		String username = "existingUser";
		String password = "password";
		Login existingLogin = new Login();
		existingLogin.setUser(username);

		when(loginRepository.findByUser(username)).thenReturn(Optional.of(existingLogin));

		// Act
		String result = authController.register(username, password, model, session);

		// Assert
		assertEquals("register", result);
		verify(model).addAttribute("error", "Логин уже занят.");
	}
}

class EventControllerTest {

	@InjectMocks
	private EventController eventController;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private LoginRepository loginRepository;

	@Mock
	private ApplicationRepository applicationRepository;

	@Mock
	private IdeaRepository ideaRepository;

	@Mock
	private HttpSession session;

	@Mock
	private Model model;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testEventsPage_UserLoggedIn_ReturnsEventsPage() {
		// Arrange
		String username = "testUser";
		when(session.getAttribute("user")).thenReturn(username);

		Login mockLogin = new Login();
		mockLogin.setId(1);
		when(loginRepository.findByUser(username)).thenReturn(Optional.of(mockLogin));

		List<Event> events = new ArrayList<>();
		Event event = new Event();
		event.setId(1);
		event.setStatus("Open");
		events.add(event);
		when(eventRepository.findAll()).thenReturn(events);

		List<Team> teams = new ArrayList<>();
		when(teamRepository.findByUserId(1)).thenReturn(teams);

		// Act
		String result = eventController.eventsPage(session, model);

		// Assert
		assertEquals("events", result);
		verify(model).addAttribute(eq("availableEvents"), any());
		verify(model).addAttribute(eq("userEvents"), any());
		verify(model).addAttribute(eq("eventParticipants"), any());
		verify(model).addAttribute(eq("eventImages"), any());
	}

	@Test
	void testEventsPage_UserNotLoggedIn_RedirectsToLogin() {
		// Arrange
		when(session.getAttribute("user")).thenReturn(null);

		// Act
		String result = eventController.eventsPage(session, model);

		// Assert
		assertEquals("redirect:/login", result);
	}

	@Test
	void testApplyForEvent_NewApplication_Success() {
		// Arrange
		String username = "testUser";
		int eventId = 1;

		when(session.getAttribute("user")).thenReturn(username);

		Login mockLogin = new Login();
		mockLogin.setId(1);
		when(loginRepository.findByUser(username)).thenReturn(Optional.of(mockLogin));

		when(applicationRepository.findByUserIdAndEventId(1, eventId)).thenReturn(Optional.empty());

		// Act
		String result = eventController.applyForEvent(eventId, session, model);

		// Assert
		assertEquals("events", result);
		verify(applicationRepository).save(any(Application.class));
		verify(model).addAttribute("successEventId", eventId);
	}

	@Test
	void testApplyForEvent_ExistingApplication_Error() {
		// Arrange
		String username = "testUser";
		int eventId = 1;

		when(session.getAttribute("user")).thenReturn(username);

		Login mockLogin = new Login();
		mockLogin.setId(1);
		when(loginRepository.findByUser(username)).thenReturn(Optional.of(mockLogin));

		Application existingApplication = new Application();
		when(applicationRepository.findByUserIdAndEventId(1, eventId)).thenReturn(Optional.of(existingApplication));

		// Act
		String result = eventController.applyForEvent(eventId, session, model);

		// Assert
		assertEquals("events", result);
		verify(model).addAttribute("errorEventId", eventId);
	}

	@Test
	void testSubmitIdea_ValidIdea_Success() {
		// Arrange
		String username = "testUser";
		String title = "New Idea";
		String description = "Idea Description";

		when(session.getAttribute("user")).thenReturn(username);

		Login mockLogin = new Login();
		mockLogin.setId(1);
		when(loginRepository.findByUser(username)).thenReturn(Optional.of(mockLogin));

		// Act
		String result = eventController.submitIdea(title, description, session, model);

		// Assert
		assertEquals("redirect:/events", result);
		verify(ideaRepository).save(any(Idea.class));
		verify(model).addAttribute("message", "Idea submitted successfully!");
	}

	@Test
	void testSubmitIdea_UserNotLoggedIn_RedirectsToLogin() {
		// Arrange
		when(session.getAttribute("user")).thenReturn(null);

		// Act
		String result = eventController.submitIdea("Title", "Description", session, model);

		// Assert
		assertEquals("redirect:/login", result);
	}
}

class ManagerControllerTest {

	@InjectMocks
	private ManagerController managerController;

	@Mock
	private ApplicationRepository applicationRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private LoginRepository loginRepository;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private HttpSession session;

	@Mock
	private Model model;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testManagerPage_UserLoggedInAsManager_ReturnsManagerPage() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		when(applicationRepository.findAll()).thenReturn(Collections.emptyList());
		when(loginRepository.findAll()).thenReturn(Collections.emptyList());
		when(eventRepository.findAll()).thenReturn(Collections.emptyList());

		// Act
		String result = managerController.managerPage(session, model);

		// Assert
		assertEquals("manager", result);
		verify(model).addAttribute(eq("apps"), any());
		verify(model).addAttribute(eq("userMap"), any());
		verify(model).addAttribute(eq("eventMap"), any());
		verify(model).addAttribute(eq("events"), any());
	}

	@Test
	void testManagerPage_UserNotLoggedIn_RedirectsToLogin() {
		// Arrange
		when(session.getAttribute("user")).thenReturn(null);

		// Act
		String result = managerController.managerPage(session, model);

		// Assert
		assertEquals("redirect:/login", result);
	}

	@Test
	void testAcceptApplication_ValidApplication_AcceptsSuccessfully() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int appId = 1;
		int teamId = 101;

		Application app = new Application();
		app.setId(appId);
		app.setUserId(10);
		app.setEventId(20);

		when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

		// Act
		String result = managerController.acceptApplication(appId, teamId, session, model);

		// Assert
		assertEquals("manager", result);
		verify(teamRepository).save(any(Team.class));
		verify(applicationRepository).delete(app);
		verify(model).addAttribute(eq("successMessage"), eq("Application accepted and added to team successfully!"));
	}

	@Test
	void testAcceptApplication_InvalidApplication_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int appId = 1;
		int teamId = 101;

		when(applicationRepository.findById(appId)).thenReturn(Optional.empty());

		// Act
		String result = managerController.acceptApplication(appId, teamId, session, model);

		// Assert
		assertEquals("manager", result);
		verify(model).addAttribute(eq("errorMessage"), eq("Application not found."));
	}

	@Test
	void testRejectApplication_ValidApplication_RejectsSuccessfully() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int appId = 1;

		when(applicationRepository.existsById(appId)).thenReturn(true);

		// Act
		String result = managerController.rejectApplication(appId, session, model);

		// Assert
		assertEquals("manager", result);
		verify(applicationRepository).deleteById(appId);
		verify(model).addAttribute(eq("successMessage"), eq("Application rejected successfully."));
	}

	@Test
	void testRejectApplication_InvalidApplication_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int appId = 1;

		when(applicationRepository.existsById(appId)).thenReturn(false);

		// Act
		String result = managerController.rejectApplication(appId, session, model);

		// Assert
		assertEquals("manager", result);
		verify(model).addAttribute(eq("errorMessage"), eq("Application not found."));
	}

	@Test
	void testChangeEventStatus_EventExists_ChangesStatus() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int eventId = 1;
		String newStatus = "Closed";

		Event event = new Event();
		event.setId(eventId);
		event.setStatus("Open");

		when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

		// Act
		String result = managerController.changeEventStatus(eventId, newStatus, session, model);

		// Assert
		assertEquals("manager", result);
		verify(eventRepository).save(event);
		verify(model).addAttribute(eq("eventSuccessMessage"), eq("Event status updated successfully!"));
		assertEquals(newStatus, event.getStatus());
	}

	@Test
	void testChangeEventStatus_EventNotFound_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("managerUser");
		when(session.getAttribute("role")).thenReturn("manager");

		int eventId = 1;
		String newStatus = "Closed";

		when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

		// Act
		String result = managerController.changeEventStatus(eventId, newStatus, session, model);

		// Assert
		assertEquals("manager", result);
		verify(model).addAttribute(eq("eventErrorMessage"), eq("Event not found."));
	}
}

class AdminControllerTest {

	@InjectMocks
	private AdminController adminController;

	@Mock
	private EventRepository eventRepository;

	@Mock
	private IdeaRepository ideaRepository;

	@Mock
	private LoginRepository loginRepository;

	@Mock
	private TeamRepository teamRepository;

	@Mock
	private HttpSession session;

	@Mock
	private Model model;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	void testAdminPage_UserLoggedInAsAdmin_ReturnsAdminPage() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("adminUser");
		when(session.getAttribute("role")).thenReturn("admin");

		when(ideaRepository.findAll()).thenReturn(Collections.emptyList());
		when(loginRepository.findAll()).thenReturn(Collections.emptyList());

		// Act
		String result = adminController.adminPage(session, model);

		// Assert
		assertEquals("admin", result);
		verify(model).addAttribute(eq("ideaError"), eq("No ideas available."));
		verify(model).addAttribute(eq("loginError"), eq("No logins available."));
	}

	@Test
	void testAdminPage_UserNotLoggedIn_RedirectsToLogin() {
		// Arrange
		when(session.getAttribute("user")).thenReturn(null);

		// Act
		String result = adminController.adminPage(session, model);

		// Assert
		assertEquals("redirect:/login", result);
	}

	@Test
	void testCreateEvent_ExistingEvent_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("adminUser");
		when(session.getAttribute("role")).thenReturn("admin");

		String title = "Test Event";
		String description = "Test Description";

		Event existingEvent = new Event();
		when(eventRepository.findByTitle(title)).thenReturn(Optional.of(existingEvent));

		// Act
		String result = adminController.createEvent(title, description, session, model);

		// Assert
		assertEquals("admin", result);
		verify(model).addAttribute(eq("createError"), eq("An event with this title already exists."));
	}

	@Test
	void testDeleteEvent_EventExists_DeletesSuccessfully() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("adminUser");
		when(session.getAttribute("role")).thenReturn("admin");

		String eventTitle = "Test Event";

		Event existingEvent = new Event();
		when(eventRepository.findByTitle(eventTitle)).thenReturn(Optional.of(existingEvent));

		// Act
		String result = adminController.deleteEvent(eventTitle, session, model);

		// Assert
		assertEquals("admin", result);
		verify(eventRepository).delete(existingEvent);
		verify(model).addAttribute(eq("deleteMessage"), eq("Event deleted successfully!"));
	}

	@Test
	void testDeleteEvent_EventNotFound_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("adminUser");
		when(session.getAttribute("role")).thenReturn("admin");

		String eventTitle = "Nonexistent Event";

		when(eventRepository.findByTitle(eventTitle)).thenReturn(Optional.empty());

		// Act
		String result = adminController.deleteEvent(eventTitle, session, model);

		// Assert
		assertEquals("admin", result);
		verify(model).addAttribute(eq("deleteError"), eq("An event with this title does not exist."));
	}

	@Test
	void testSelectIdea_IdeaNotFound_ReturnsError() {
		// Arrange
		when(session.getAttribute("user")).thenReturn("adminUser");
		when(session.getAttribute("role")).thenReturn("admin");

		int ideaId = 999;

		when(ideaRepository.findById(ideaId)).thenReturn(Optional.empty());

		// Act
		String result = adminController.selectIdea(ideaId, session, model);

		// Assert
		assertEquals("admin", result);
		verify(model).addAttribute(eq("errorMessage"), eq("Idea not found."));
	}
}
